package com.deciphernow.greymatter.data

import java.io.{ByteArrayInputStream, File, FileInputStream}
import java.security.KeyStore
import java.time.LocalDateTime

import cats.effect.IO
import com.deciphernow.greymatter.data.nifi.http.Metadata
import com.deciphernow.greymatter.data.nifi.processors.utils.GetOidForPathStreamingFunctions
import com.deciphernow.greymatter.data.nifi.properties.CommonProperties
import com.deciphernow.greymatter.data.nifi.relationships.ProcessorRelationships
import io.circe.Json
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.ssl.SSLContextService.ClientAuth
import org.apache.nifi.ssl.StandardSSLContextService
import org.apache.nifi.util.TestRunner
import org.http4s.{Headers, Uri}
import org.http4s.client.Client
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.util.Random

trait TestContext extends CommonProperties with ProcessorRelationships with GetOidForPathStreamingFunctions {

  import scala.collection.JavaConverters._

  val objectPolicySimple = """{"label":"forAnonReadRobFull","requirements":{"f":"owner-full-ro-all","a":[{"v":"email"},{"v":"nifinpe@example.com"}]}}"""
  val objectPolicyOrganizationA = """{"label":"OrganizationAPolicy","requirements":{"f":"if","a":[{"f":"contains","a":[{"v":"org"},{"v":"OrganizationA"}]},{"f":"yield","a":[{"v":"C"},{"v":"R"},{"v":"U"},{"v":"D"},{"v":"P"},{"v":"X"}]}]}}"""
  val policies = List(objectPolicySimple, objectPolicyOrganizationA)
  val commonOptionalProperties = Map((originalObjectPolicyProperty, "placeholder"), (securityProperty, """{"label":"something","foreground":"something","background":"something"}"""))
  val gmDataUrl = "https://0.0.0.0:8181"
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

  def getUserFolderOid(objectPolicy: Json)(implicit sslContext: SSLContext, rootUrl: Uri, headers: Headers, client: Client[IO])  = for {
    userFolderPath <- getUserFolderPath(rootUrl, headers)
    userMetadata = List(createFolderMetadata(npeEmail)(userFolderPath.split("/").head, objectPolicy, "U"))
    userFolderOid <- writeFolder(userMetadata, rootUrl, headers).map(_.oid.get)
  } yield userFolderOid

  private def createMetadata(parentoid: String, objectPolicy: Json, action: String, mimeType: Option[String] = Some("text/plain"), name: String = randomString(), isFile: Option[Boolean] = Some(true)) = {
    Metadata(parentoid, name, objectPolicy, mimeType, None, action, None, None, None, None, None, None, None, None, isfile = isFile)
  }
  private def createFolderMetadata(name: String = randomString()) = createMetadata(_, _, _, None, name, None)

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

}
