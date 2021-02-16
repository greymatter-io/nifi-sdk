package com.deciphernow.greymatter.data.nifi.processors

import cats.effect.{ ContextShift, IO, Timer }
import cats.implicits._
import fs2.Stream
import com.deciphernow.greymatter.data.nifi.processors.utils.PrepareWriteRequestUtils

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

// NiFi
import org.apache.nifi.annotation.behavior.{ ReadsAttribute, ReadsAttributes }
import org.apache.nifi.annotation.documentation.{ CapabilityDescription, SeeAlso, Tags }
import org.apache.nifi.annotation.lifecycle.OnScheduled
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.processor.{ AbstractProcessor, ProcessSession, ProcessorInitializationContext, Relationship, ProcessContext }

@Tags(Array("gmdata"))
@CapabilityDescription("A processor that builds a GMData-compatible request body for a given file path.")
@SeeAlso(Array())
@ReadsAttributes(Array(
  new ReadsAttribute(attribute = "mime.type", description = "mime type from processor"),
  new ReadsAttribute(attribute = "filename", description = "filename from list file processor")))
class PrepareWriteRequest extends AbstractProcessor with PrepareWriteRequestUtils {

  import scala.collection.JavaConverters._

  protected[this] override def init(context: ProcessorInitializationContext): Unit = {
  }

  override def getSupportedPropertyDescriptors: java.util.List[PropertyDescriptor] = {
    prepareWriteRequestProperties.asJava
  }

  override def getRelationships: java.util.Set[Relationship] = {
    relationships.asJava
  }

  lazy val ec = ExecutionContext.global
  implicit val ctxShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  @OnScheduled
  def onScheduled(context: ProcessContext): Unit = {
  }

  override def onTrigger(context: ProcessContext, session: ProcessSession) =
    Try {
      (for {
        logger <- Stream.eval(IO.delay(getLogger))
        flowfileEither <- Stream.eval(getFlowFile(session, logger))
        result <- flowfileEither.flatTraverse(writeMetadataToFlowfile(context, session, _, logger))

      } yield result).compile.toList.unsafeRunSync()
    } match {
      case Success(_) =>
      case Failure(err) =>
        val errorStack = err.getStackTrace.mkString("\n")
        throw new Error(s"There was an error processing the flowfile: $err . Error stack: $errorStack")
    }
}
