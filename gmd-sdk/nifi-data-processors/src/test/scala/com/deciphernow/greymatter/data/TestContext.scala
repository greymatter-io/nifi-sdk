package com.deciphernow.greymatter.data

import java.io.{ByteArrayInputStream, File, FileInputStream}
import java.security.KeyStore
import java.time.LocalDateTime

import cats.effect.{ConcurrentEffect, IO}
import cats.implicits._
import com.deciphernow.greymatter.data.nifi.http.Metadata
import com.deciphernow.greymatter.data.nifi.processors.utils.GetOidForPathUtils
import com.deciphernow.greymatter.data.nifi.properties.CommonProperties
import com.deciphernow.greymatter.data.nifi.relationships.ProcessorRelationships
import fs2.{Pure, Stream}
import io.circe.{Json, Printer}
import io.circe.syntax._
import io.circe.generic.auto._
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.security.util.ClientAuth
import org.apache.nifi.ssl.StandardSSLContextService
import org.apache.nifi.util.TestRunner
import org.http4s.{Headers, MediaType, Method, Uri}
import org.http4s.client.Client
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Multipart, Part}

import scala.concurrent.ExecutionContext
import scala.util.Random

trait TestContext extends CommonProperties with ProcessorRelationships with GetOidForPathUtils {

  import scala.collection.JavaConverters._

  val objectPolicySimple = """{"label":"forAnonReadRobFull","requirements":{"f":"owner-full-ro-all","a":[{"v":"email"},{"v":"nifinpe@example.com"}]}}"""
  val objectPolicyOrganizationA = """{"label":"OrganizationAPolicy","requirements":{"f":"if","a":[{"f":"contains","a":[{"v":"org"},{"v":"OrganizationA"}]},{"f":"yield","a":[{"v":"C"},{"v":"R"},{"v":"U"},{"v":"D"},{"v":"P"},{"v":"X"}]}]}}"""
  val policies = List(objectPolicySimple, objectPolicyOrganizationA)
  val commonOptionalProperties = Map((originalObjectPolicyProperty, "placeholder"), (securityProperty, """{"label":"something","foreground":"something","background":"something"}"""))
  val gmDataUrl = "https://0.0.0.0:8181/"
  val npeEmail = "nifinpe@example.com"
  val sslServiceName = "ssl-context"

  def addSSLService(runner: TestRunner, sslProperty: PropertyDescriptor, serviceName: String = sslServiceName) = {
    val sslProperties = Map((StandardSSLContextService.KEYSTORE.getName, "../certs/nifinpe.jks"),
      (StandardSSLContextService.KEYSTORE_PASSWORD.getName, "bmlmaXVzZXIK"),
      (StandardSSLContextService.KEYSTORE_TYPE.getName, "JKS"),
      (StandardSSLContextService.TRUSTSTORE.getName, "../certs/rootCA.jks"),
      (StandardSSLContextService.TRUSTSTORE_PASSWORD.getName, "devotion"),
      (StandardSSLContextService.TRUSTSTORE_TYPE.getName, "JKS"))
    val service = new StandardSSLContextService()
    runner.addControllerService(sslServiceName, service, sslProperties.asJava)
    runner.enableControllerService(service)
    runner.setProperty(sslProperty, sslServiceName)
    getSSLContext(runner, sslServiceName)
  }

  def getSSLContext(runner: TestRunner, serviceName: String = sslServiceName) = runner.getControllerService(serviceName, classOf[StandardSSLContextService]).createSSLContext(ClientAuth.NONE)


  def checkForFolders(path: String, headers: Headers)(implicit rootUrl: Uri, client: Client[IO]) = {
    val folderList = path.split("/").filter(name => name.nonEmpty && name != ".")
    folderList.drop(1).foldLeft(IO(folderList.head)) { (lastOidEither, folderName) =>
      lastOidEither.flatMap(checkForOid(folderName, _, headers)).map(_.right.get)
    }
  }

  def getUserFolderPath(rootUrl: Uri, headers: Headers)(implicit client: Client[IO]) = for {
    config <- getConfig(rootUrl, client, headers)
    self <- getSelf(rootUrl, client, headers)
    userField = self.getUserField(config.GMDATA_NAMESPACE_USERFIELD).right.get
  } yield s"${config.GMDATA_NAMESPACE_OID}/$userField"

  def getUserFolderOid(objectPolicy: Json)(implicit rootUrl: Uri, headers: Headers, client: Client[IO])  = for {
    userFolderPath <- getUserFolderPath(rootUrl, headers)
    userMetadata = List(createFolderMetadata(npeEmail)(userFolderPath.split("/").head, objectPolicy, "U"))
    userFolderOid <- writeFolder(userMetadata, rootUrl, headers).map(_.oid.get)
  } yield userFolderOid

  def createMetadata(parentoid: String, objectPolicy: Json, action: String, mimeType: Option[String] = Some("text/plain"), name: String = randomString(), isFile: Option[Boolean] = Some(true)) = {
    Metadata(parentoid, name, objectPolicy, mimeType, None, action, None, None, None, None, None, None, None, None, isfile = isFile)
  }
  def createFolderMetadata(name: String = randomString()) = createMetadata(_, _, _, None, name, None)

  def createIntermediateFolders(parentOid: String, path: List[String], objPolicy: Json, action: String = "C")(implicit rootUrl: Uri, headers: Headers, client: Client[IO]) = {
    path.foldLeft(IO(parentOid)){ (lastOid, name) =>
      lastOid.flatMap{ oid =>
        val props = createFolderMetadata(name)(oid, objPolicy, action)
        writeFolder(List(props), rootUrl, headers).map(_.oid.get)
      }
    }
  }

  def pathToList(path: String) = path.split("/").filter(name => name.nonEmpty && name != ".").toList

  def randomString(length: Int = 5, prefix: Option[String] = Some(LocalDateTime.now().toString)) = prefix.getOrElse("") + Random.alphanumeric.take(length).mkString

  def loadFlowFileIntoRunner(contentString: String, attributes: Map[String, String])(runner: TestRunner) = {
    val content = new ByteArrayInputStream(contentString.getBytes)
    runner.enqueue(content, Map[String, String]().asJava)
  }

  def createSslContext(keyStoreFile: File,
                       keyStorePassword: String,
                       trustStoreFile: File,
                       trustStorePassword: String): SSLContext = {

    val keyStoreStream = new FileInputStream(keyStoreFile)
    val trustStoreStream = new FileInputStream(trustStoreFile)
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val keyPass = keyStorePassword.toCharArray
    val trustPass = trustStorePassword.toCharArray
    keyStore.load(keyStoreStream, keyPass)
    keyManagerFactory.init(keyStore, keyPass)
    trustStore.load(trustStoreStream, trustPass)
    trustManagerFactory.init(trustStore)
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)
    sslContext
  }

  def createRandomFiles(parentOid: String, objectPolicy: Json, action: String, directory: String, fileStream: Stream[Pure, Byte], name: String, folderName: String)(levels: Int, filesPerLevel: Int, foldersPerLevel: Int)(implicit rootUrl: Uri, headers: Headers, client: Client[IO]) = for {
    parentOid <- writeFolder(List(createFolderMetadata(directory)(parentOid, objectPolicy, "U")), rootUrl, headers).map(_.oid.get)
    files <- createFiles(objectPolicy, action, parentOid, fileStream, name, folderName)("/", levels, filesPerLevel, foldersPerLevel)
  } yield files

  def createFiles(objectPolicy: Json, action: String, parentOid: String, fileStream: Stream[Pure, Byte], name: String, folderName: String)(path: String, levels: Int, filesPerLevel: Int, foldersPerLevel: Int)(implicit rootUrl: Uri, headers: Headers, client: Client[IO]): IO[List[Metadata]] = for {
    files <- List.fill(filesPerLevel)(createMetadata(parentOid, objectPolicy, action, name = name)).traverse { metadata =>
      writeFile(List(metadata), rootUrl, headers, fileStream)
    }.map(_.map(_.copy(relativePath = Some(path))))
    folders <- List.fill(foldersPerLevel)(createFolderMetadata(folderName)(parentOid, objectPolicy, action)).traverse(metadata => writeFolder(List(metadata), rootUrl, headers))
    emptyMetadata = IO(List[Metadata]())
    moreFiles <- {
      if (levels > 0) folders.foldLeft(emptyMetadata) { (all, folder) =>
        all.flatMap { list =>
          val newPath = s"$path${folder.name}/"
          createFiles(objectPolicy, action, folder.oid.get, fileStream, name, folderName)(newPath, levels - 1, filesPerLevel, foldersPerLevel).map(_ ++ list)
        }
      }
      else emptyMetadata
    }
  } yield files ++ moreFiles

  def createRandomFilesAndMapRootUrl(parentOid: String, client: Client[IO], objectPolicy: Json, action: String, directory: String, rootUrl: String, levels: Int, numberOfFiles: Int, numberOfFolders: Int, headers: Headers, fileStream: Stream[Pure, Byte] = Stream.emits("".getBytes), name: String = randomString(), folderName: String = randomString())
                                    (implicit ec: ExecutionContext, ce: ConcurrentEffect[IO]) =
    createRandomFiles(parentOid, objectPolicy, action, directory, fileStream, name, folderName)(levels, numberOfFiles, numberOfFolders)(Uri.unsafeFromString(rootUrl.stripSuffix("/")), headers, client).map(_.map(_.copy(rootUrlOption = Some(rootUrl.stripSuffix("/")))))



  def writeFile(metadata: List[Metadata], rootUrl: Uri, headers: Headers, fileStream: Stream[Pure, Byte])(implicit client: Client[IO]) = {
    val printer = Printer.spaces2.copy(dropNullValues = true)
    val body = metadata.asJson.pretty(printer)
    val multipart = createMultipart(body, metadata.head.name, fileStream)
    val request = Method.POST(multipart, rootUrl / "write")
    writeToGmData[List[Metadata]](client, multipart.headers ++ headers, request, defaultHandleResponseFunction[List[Metadata]]).map(_.head)
  }

  def createMultipart(metadata: String, fileName: String, fileStream: Stream[Pure, Byte]) = {
    val contentType = `Content-Type`(MediaType.application.json)
    Multipart[IO](Vector(Part.formData("meta", metadata), Part.fileData("blob", fileName, fileStream, contentType)))
  }

  def iterateOrFail(runner: TestRunner, end: Int = 10,  start: Int = 0)(successCondition: TestRunner => Boolean): Unit = if(!successCondition(runner) && start < end) {
    runner.run()
    iterateOrFail(runner, end, start +1)(successCondition)
  }

}
