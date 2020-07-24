package com.deciphernow.greymatter.data.nifi.processors.utils

import cats.effect.{ Blocker, ContextShift, IO }
import cats.implicits._
import com.deciphernow.greymatter.data.nifi.http.{ Metadata, Security }
import com.deciphernow.greymatter.data.nifi.properties.PrepareWriteRequestProperties
import com.deciphernow.greymatter.data.nifi.relationships.ProcessorRelationships
import com.deciphernow.greymatter.data.{ Action, ParentOid }
import fs2.Stream
import io.circe.{ Json, Printer }
import io.circe.syntax._
import io.circe.generic.auto._
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.{ ProcessContext, ProcessSession, Relationship }

import scala.util.Random

trait PrepareWriteRequestStreamingFunctions extends PrepareWriteRequestProperties with ProcessorRelationships with ErrorHandling with ProcessorUtils {

  protected def writeSession(inputStream: Stream[IO, Byte], blocker: Blocker, session: ProcessSession, flowFile: FlowFile)(implicit cs: ContextShift[IO]) = {
    inputStream.through(fs2.io.writeOutputStream(IO(session.write(flowFile)), blocker)).attempt.reduce { (last, curr) =>
      last.flatMap(_ => curr)
    }
  }

  protected def inputStream(chunkSize: Int, blocker: Blocker, preMetadata: Array[Byte], postMetadata: Array[Byte], session: ProcessSession, flowFile: FlowFile)(implicit cs: ContextShift[IO]) =
    Stream.emits(preMetadata) ++ fs2.io.readInputStream(IO(session.read(flowFile)), chunkSize, blocker) ++ Stream.emits(postMetadata)

  private val firstMeta = (boundary: String) =>
    s"""
      |--$boundary
      |Content-Disposition: form-data; name="metadata"
      |
      |[
      |
      |""".stripMargin.getBytes

  private val secondMeta = (fileName: String, boundary: String) =>
    s"""
       |]
       |
       |--$boundary
       |Content-Disposition: form-data; name="file"; filename="$fileName"
       |
       |""".stripMargin.getBytes

  private val thirdMeta = (boundary: String) =>
    s"""
      |--$boundary--
      |
      |""".stripMargin.getBytes

  private def transferResult(logger: ComponentLog, flowFile1: FlowFile, transfer: (Relationship, FlowFile) => IO[Unit], remove: FlowFile => IO[Unit])(either: Either[Throwable, FlowFile]) = (either match {
    case Right(flowFile2) => remove(flowFile1).flatMap(_ => transfer(RelSuccess, flowFile2)).attempt
    case Left(err) => IO.delay(logger.error(err.getMessage)).flatMap(_ => transfer(RelFailure, flowFile1)).attempt
  }) flatMap logTransferResult(logger)

  def buildMetadata(boundary: String,
    oid: ParentOid,
    fileNameEither: Either[Throwable, String],
    objectPolicy: Json,
    mimeTypeEither: Either[Throwable, String],
    sizeEither: Either[Throwable, String],
    actionEither: Either[Throwable, Action],
    security: Option[Security],
    originalObjectPolicy: Option[String],
    custom: Option[Json]) = for {
    action <- actionEither
    fileName <- fileNameEither
    mimeType <- mimeTypeEither
    size <- sizeEither
  } yield firstMeta(boundary) ++
    Metadata(parentoid = oid.value,
      name = fileName,
      objectpolicy = objectPolicy,
      mimetype = Some(mimeType),
      size = Some(size.toLong),
      action = action.value,
      security = security,
      originalObjectPolicy = originalObjectPolicy,
      custom = custom,
      oid = None,
      tstamp = None,
      relativePath = None,
      policy = None,
      isdir = None, isfile = Some(true)).asJson.pretty(Printer.noSpaces.copy(dropNullValues = true)).getBytes ++
    secondMeta(fileName, boundary)

  private def getMetadata(implicit flowFile: FlowFile, context: ProcessContext, boundary: String) = for {
    objectPolicy <- IO.fromEither(parseObjectPolicy).attempt.map(handleErrorAndShutdown("The Object Policy property was not correctly set"))
    oid <- IO.delay(parseParentOid).attempt map handleErrorAndShutdown("The oid property was not correctly set")
    actionEither <- IO.delay(parseAction).attempt map handleErrorAndContinue("The action attribute was not able to be parsed from the flowfile")
    fileNameEither <- IO.delay(parseFilename).attempt map handleErrorAndContinue("The filename attribute was not able to be parsed from the flowfile")
    mimeTypeEither <- IO.delay(parseMimeType).attempt map handleErrorAndContinue("The mime.type attribute was not able to be parsed from the flowfile")
    sizeEither <- IO.delay(parseSize).attempt map handleErrorAndContinue("The size attribute was not able to be parsed from the flowfile")
    security <- IO.delay(parseSecurity) map getOptionalProperty("The Security property was not correctly set")
    originalObjectPolicy <- IO.delay(parseOriginalObjectPolicy)
    custom <- IO.delay(parseCustom) map getOptionalProperty("The custom property was not correctly set")
  } yield buildMetadata(boundary, oid, fileNameEither, objectPolicy, mimeTypeEither, sizeEither, actionEither, security, originalObjectPolicy, custom)

  private def createBoundary = Random.alphanumeric.take(100).mkString.getBytes().take(30).map("%02x".format(_)).mkString

  private def writeContentToFlowFile(oldFlowFile: FlowFile, session: ProcessSession, boundary: String, blocker: Blocker, chunkSize: Int)(preMetadata: Array[Byte])(implicit cs: ContextShift[IO]) = for {
    flowFile2 <- Stream.eval(IO.delay(session.clone(oldFlowFile)))
    updatedContentStream = inputStream(chunkSize, blocker, preMetadata, thirdMeta(boundary), session, oldFlowFile)
    result <- writeSession(updatedContentStream, blocker, session, flowFile2).map(successEither => successEither.map(_ => flowFile2))
  } yield result

  def writeMetadataToFlowfile(context: ProcessContext, session: ProcessSession, flowFile: FlowFile, logger: ComponentLog)(implicit cs: ContextShift[IO]) = for {
    blocker <- Stream.resource(Blocker[IO])
    chunkSize <- Stream.eval(IO.delay(parseChunkSize(flowFile)))
    boundary = createBoundary
    preMetadataEither <- Stream.eval(getMetadata(flowFile, context, boundary))
    _ <- Stream.eval(updateAttribute("mime.type", s"multipart/form-data; boundary=$boundary")(flowFile, session))
    writeResult <- preMetadataEither.flatTraverse(writeContentToFlowFile(flowFile, session, boundary, blocker, chunkSize))
    finalFlowFile <- Stream.eval(sendErrorsAsAttributes("preparewriterequest", flowFile, session, writeResult))
    result <- Stream.eval(transferResult(logger, finalFlowFile, transferFlowfile(session), removeFlowfile(session))(writeResult))
  } yield result
}
