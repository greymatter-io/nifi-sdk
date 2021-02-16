package com.deciphernow.greymatter.data.nifi.processors.utils

import cats.effect.concurrent.Ref
import cats.effect.{ ContextShift, IO }
import cats.implicits._
import com.deciphernow.greymatter.data.nifi.http.{ Metadata, GmDataClient }
import com.deciphernow.greymatter.data.nifi.properties.{ GetOidForPathConfig, GetOidForPathProperties }
import com.deciphernow.greymatter.data.nifi.relationships.ProcessorRelationships
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.{ ProcessContext, ProcessSession }
import org.http4s.client.Client
import org.http4s.{ Headers, Uri }

trait GetOidForPathUtils extends GetOidForPathProperties with ProcessorRelationships with GmDataClient[IO] with ErrorHandling with ProcessorUtils {

  private def createFolders(implicit context: ProcessContext, flowFile: FlowFile, client: Client[IO]) = for {
    config <- getPropertyConfig
    userFolderOid <- getUserFolderOid(config)
    intermediateOid <- config.intermediateFolders.traverse(getFinalFolderOid(userFolderOid, _, config.intermediateMetadata, config)).map(_.getOrElse(userFolderOid))
    finalOid <- config.folders.flatTraverse(getFinalFolderOid(intermediateOid, _, config.metadata(_, _), config))
  } yield finalOid

  private def getFinalFolderOid(defaultOid: Either[Throwable, String], folderList: List[String], getMetadata: (String, String) => Metadata, config: GetOidForPathConfig)(implicit client: Client[IO]) = folderList.foldLeft(IO(defaultOid)) { (getLastOid, folderName) =>
    getLastOid.flatMap(_.flatTraverse(getFolderOid(folderName, getMetadata, config)))
  }

  private def getFolderOid(folderName: String, getMetadata: (String, String) => Metadata, config: GetOidForPathConfig)(parentOid: String)(implicit client: Client[IO]) = {
    val metadata = List(getMetadata(parentOid, folderName))
    getOrWriteFolderOid(parentOid, metadata, folderName, config)
  }

  private def getValidFolderProps(path: Uri.Path, headers: Headers)(implicit rootUrl: Uri, client: Client[IO]) = getFolderProps(path, headers).attempt.map(_.flatMap(getValidProps))

  private def getUserFolderOid(propertyConfig: GetOidForPathConfig)(implicit client: Client[IO]): IO[Either[Throwable, String]] = for {
    configEither <- propertyConfig.folders.flatTraverse(_ => getConfig(propertyConfig.rootUrl, client, propertyConfig.headers).attempt map handleErrorAndContinue("There was an error hitting the /config endpoint of GM Data"))
    userFolderOid <- configEither.flatTraverse{ config =>
      for{
        userFolder <- getUserFolder(propertyConfig.rootUrl, propertyConfig.headers, config, client)
        oid <- userFolder.flatTraverse(getFolderOid(_, propertyConfig.userMetadata, propertyConfig)(config.GMDATA_NAMESPACE_OID))
      } yield oid
    }
  } yield userFolderOid

  private def getOrWriteFolderOid(parentOidAsPath: Uri.Path, metadata: List[Metadata], nameOfFolder: String, config: GetOidForPathConfig)(implicit client: Client[IO]) = checkForOid(nameOfFolder, parentOidAsPath, config.headers)(config.rootUrl, client).flatMap {
    case Right(oid) => IO(Right(oid))
    case Left(err) => writeFolder(metadata, config.rootUrl, config.headers).attempt map handleErrorAndContinue("There was an error hitting the /write endpoint of GM Data:") map (_.map(_.oid.get))
  }

  protected def checkForOid(nameOfFolder: String, parentOidAsPath: Uri.Path, headers: Headers)(implicit rootUrl: Uri, client: Client[IO]) = getValidFolderProps(s"$parentOidAsPath/$nameOfFolder", headers).flatMap {
    case Right(props) => IO(Right(props))
    case _ => getFileList(parentOidAsPath + "/", headers).attempt.map(handleErrorAndContinue("There was an error hitting the /list endpoint of GM Data")).map(_.flatMap(filterByName(nameOfFolder)))
  } map (_.map(_.oid.get))

  private def filterByName(userField: String)(metadatas: List[Metadata]) = {
    val list = metadatas.filter(metadata => metadata.isfile.isEmpty && metadata.name == userField)
    if (list.nonEmpty) Right(list.head) else Left(new Throwable("No folders could be found"))
  }

  private def getValidProps(metadata: Metadata) = {
    if (Set("C", "R").subsetOf(metadata.policy.get.policy.toSet)) Right(metadata)
    else Left(new Throwable(s"Insufficient create/read access for folder ${metadata.name}"))
  }

  private def updateOidAttribute(oid: String)(implicit flowFile: FlowFile, session: ProcessSession) = updateAttribute("gmdata.parentoid", oid)

  protected def startProcessing(logger: ComponentLog)(implicit context: ProcessContext, session: ProcessSession, flowFile: FlowFile, clientRef: Ref[IO, Client[IO]], ctxShift: ContextShift[IO]) = for {
    client <- clientRef.get
    oidEither <- createFolders(context, flowFile, client)
    errors = logErrors(logger, (oid: String) => s"gmdata.parentoid attribute updated with parent oid: $oid", "There was a problem with the processor")(_)
    updateOid <- oidEither.flatTraverse(oid => updateOidAttribute(oid)(flowFile, session).map(_ => oid).attempt)
    logged <- errors(updateOid)
    finalFlowFile <- sendErrorsAsAttributes("getoidforpath", flowFile, session, logged)
    result <- transferResult(logger)(finalFlowFile, transferFlowfile(session))(logged)
  } yield result
}
