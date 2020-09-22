package com.deciphernow.greymatter.data.nifi.processors.utils

import cats.effect.concurrent.Ref
import cats.effect.{ Blocker, ContextShift, IO }
import cats.implicits._
import com.deciphernow.greymatter.data.nifi.properties.CommonProperties
import com.deciphernow.greymatter.data.nifi.relationships.ProcessorRelationships
import javax.net.ssl.SSLContext
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.{ ProcessContext, ProcessSession, Relationship }
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext

trait ProcessorUtils extends CommonProperties with ProcessorRelationships with ErrorHandling {
  protected def transferFlowfile(session: ProcessSession)(relationship: Relationship, flowFile: FlowFile)(implicit cs: ContextShift[IO]) = IO.delay(session.transfer(flowFile, relationship))

  protected def updateAttribute(key: String, value: String)(implicit flowFile: FlowFile, session: ProcessSession) = IO.delay(session.putAttribute(flowFile, key, value))

  protected def updateAttributeWithPrefix(prefix: String)(key: String, value: String)(implicit flowFile: FlowFile, session: ProcessSession) = updateAttribute(prefix + key, value)

  protected def initializeClient(context: ProcessContext, blocker: Blocker, clientRef: Ref[IO, Client[IO]])(implicit ctxShift: ContextShift[IO], ec: ExecutionContext) = {
    for {
      sslContext <- blocker.delay[IO, Option[SSLContext]](parseSSLContext(context))
      httpTimeout <- blocker.delay[IO, FiniteDuration](parseHttpTimeout(context).getOrElse(5.seconds))
      client <- BlazeClientBuilder[IO](ec, sslContext).withConnectTimeout(httpTimeout).withRequestTimeout(httpTimeout).withCheckEndpointAuthentication(false).allocated.map(_._1)
      updateClient <- clientRef.modify(old => (client, old))
    } yield updateClient
  }.unsafeRunSync()

  def transferResult[X](logger: ComponentLog)(flowFile: FlowFile, transfer: (Relationship, FlowFile) => IO[Unit])(either: Either[Throwable, X]) = (either match {
    case Right(success) => transfer(RelSuccess, flowFile).attempt
    case Left(err) => IO.delay(logger.error(err.getMessage)).flatMap(_ => transfer(RelFailure, flowFile)).attempt
  }) flatMap logTransferResult(logger)

  def sendErrorsAsAttributes[X](prefix: String = "", flowFile: FlowFile, session: ProcessSession, either: Either[Throwable, X]) = (either match {
    case Right(_) => IO(Right(flowFile))
    case Left(err) => addErrorAttributes(err, updateAttributeWithPrefix(prefix)(_, _)(_, session))(flowFile)
  }).map(_.getOrElse(flowFile))

  def getFlowFile(session: ProcessSession, logger: ComponentLog) = IO(Option(session.get).toRight("No flowfile was received but processor was triggered.")).map(_.leftMap{err =>
    logger.error(err)
    new Throwable(err)
  })

}

