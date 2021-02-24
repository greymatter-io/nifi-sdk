package com.deciphernow.greymatter.data.nifi.processors.utils

import cats.effect.{ContextShift, IO}
import cats.implicits._
import cats.effect.concurrent.Ref
import com.deciphernow.greymatter.data.nifi.http.{Config, GmDataClient, GmDataResponse}
import com.deciphernow.greymatter.data.nifi.properties.GetFilePropertiesProperties
import com.deciphernow.greymatter.data.nifi.relationships.ProcessorRelationships
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.{ProcessContext, ProcessSession}
import org.http4s.{Headers, Uri}
import org.http4s.client.Client

trait GetFilePropertiesUtils extends GmDataClient[IO] with GetFilePropertiesProperties with ProcessorRelationships with ProcessorUtils {

  def updateFlowfileAttributes(properties: GmDataResponse[String])(implicit flowFile: FlowFile, session: ProcessSession) = updateAttribute("gmdata.status.code", properties.statusCode.toString).attempt.flatMap {
    _.flatTraverse { newFlowfile =>
      updateAttribute("gmdata.file.props", properties.response)(newFlowfile, session).attempt
    }
  }.map(_.map(_ => properties))

  def validUserFolderName(headers: Headers, client: Client[IO], rootUrl: Uri)(config: Config) = getUserFolder(rootUrl, headers, config, client).map(_.map(config.GMDATA_NAMESPACE_OID + "/" + _))

  def getPropertiesAndStatusFromConfiguredPath(implicit context: ProcessContext, session: ProcessSession, flowFile: FlowFile, logger: ComponentLog, clientRef: Ref[IO, Client[IO]], cs: ContextShift[IO]) = for {
    attributesToSendRegex <- IO.delay(parseAttributesToSend(context, Some(flowFile)).map(_.r))
    headers <- IO.delay(getHeaders(attributesToSendRegex)(context, Some(flowFile)))
    rootUrl <- IO.delay(parseRootUrl(rootUrlProperty)(context, Some(flowFile)))
    filePath <- IO.delay(parseFilePath)
    fileName <- IO.delay(parseFileName)
    intermediatePrefix <- IO.delay(parseIntermediatePrefix)
    client <- clientRef.get
    configEither <- getConfig(rootUrl, client, headers).attempt map handleErrorAndContinue("There was an error hitting the /config endpoint of GM Data")
    userFolderEither <- configEither flatTraverse validUserFolderName(headers, client, rootUrl)
    propertiesEither <- userFolderEither.flatTraverse { userFolder =>
      val path = Uri.removeDotSegments(intermediatePrefix.map(intermediate => s"$userFolder/$intermediate/$filePath/$fileName").getOrElse(s"$userFolder/$filePath/$fileName"))
      getPropsAndStatus(path, headers, rootUrl, client).attempt
    }
  } yield propertiesEither

  def getFileProps(implicit context: ProcessContext, session: ProcessSession, flowFile: FlowFile, logger: ComponentLog, clientRef: Ref[IO, Client[IO]], cs: ContextShift[IO]) = for {
    propertiesEither <- getPropertiesAndStatusFromConfiguredPath
    updateAttributes <- propertiesEither flatTraverse updateFlowfileAttributes
    errors = logErrors(logger, (response: GmDataResponse[String]) => s"gmdata.status.code attribute updated with status code: ${response.statusCode}. gmdata.file.props attribute updated with response: ${response.response}", "There was a problem with the processor")(_)
    logged <- errors(updateAttributes)
    finalFlowFile <- sendErrorsAsAttributes("getfileproperties", flowFile, session, logged)
    result <- transferResult(logger)(finalFlowFile, transferFlowfile(session))(logged)
  } yield result
}
