package com.deciphernow.greymatter.data.nifi.processors.utils

import cats.effect.{ ContextShift, IO }
import cats.implicits._
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.ProcessSession

trait ErrorHandling {

  protected def handleErrorAndShutdown[X](description: String)(either: Either[Throwable, X]) = either match {
    case Right(successful) => successful
    case Left(throwable) => throw new Throwable(s"$description: $throwable")
  }

  protected def handleErrorAndContinue[X](description: String)(either: Either[Throwable, X]) = either.leftMap(err => new Throwable(s"$description: $err"))

  protected def removeFlowfile(session: ProcessSession)(flowFile: FlowFile)(implicit cs: ContextShift[IO]) = IO.delay(session.remove(flowFile))

  protected def logTransferResult[X](logger: ComponentLog)(either: Either[Throwable, X]) =
    logErrors(logger, (_: X) => "Successfully transferred flowfile", "There was an error transferring the flowfile")(either)

  protected def logErrors[X](logger: ComponentLog, successMessage: X => String, failureMessage: String)(either: Either[Throwable, X]) = either match {
    case Left(err) => IO.delay(logger.error(s"$failureMessage: $err")).map(_ => err.asLeft)
    case Right(x) => IO.delay(logger.debug(successMessage(x))).map(_ => x.asRight)
  }

  protected def addErrorAttributes[X <: Throwable](error: X, addAttribute: (String, String, FlowFile) => IO[FlowFile])(flowFile: FlowFile) = splitErrors(error) match {
    case (errorClass, message) => addAttribute(".scala.exception.class", errorClass, flowFile).attempt.flatMap { flowFileEither =>
      flowFileEither.flatTraverse(newFlowFile => addAttribute(".scala.exception.message", message, newFlowFile).attempt)
    }
  }

  private def splitErrors[X <: Throwable](error: X) = error.toString.split(" ").toList match {
    case errorClass :: messageArr => (errorClass, messageArr.mkString(" "))
  }

  protected def getOptionalProperty[A](description: String)(optProp: Option[Either[Throwable, A]]) = optProp.map(handleErrorAndShutdown(description))
}
