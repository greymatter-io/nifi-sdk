package com.deciphernow.greymatter.data.nifi.processors
import java.util.concurrent.Executors

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ContextShift, IO, Timer}
import cats.implicits._
import com.deciphernow.greymatter.data.nifi.processors.utils.GetFilePropertiesUtils
import org.apache.nifi.annotation.behavior.{DynamicProperty, ReadsAttribute, ReadsAttributes, WritesAttribute, WritesAttributes}
import org.apache.nifi.expression.ExpressionLanguageScope
import org.http4s.client.JavaNetClientBuilder

import scala.concurrent.ExecutionContext

// NiFi
import org.apache.nifi.annotation.documentation.{ CapabilityDescription, Tags }
import org.apache.nifi.annotation.lifecycle.OnScheduled
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.processor._

@Tags(Array("gmdata"))
@CapabilityDescription("Retrieves file properties of a GMData object.")
@DynamicProperty(name = "Header Name", value = "Attribute Expression Language", expressionLanguageScope = ExpressionLanguageScope.FLOWFILE_ATTRIBUTES, description = "Send request header with a key matching the Dynamic Property Key and a value created by evaluating the Attribute Expression Language set in the value of the Dynamic Property.")
@ReadsAttributes(Array(
  new ReadsAttribute(attribute = "path", description = "The path from which properties of the file are pulled."),
  new ReadsAttribute(attribute = "filename", description = "The name of the file.")
))
@WritesAttributes(Array(
  new WritesAttribute(attribute = "gmdata.status.code", description="The status code returned by GM Data when calling the props endpoint."),
  new WritesAttribute(attribute = "gmdata.file.props", description="The raw metadata of a file from GM Data or an error response.")
))
class GetFileProperties extends AbstractProcessor with GetFilePropertiesUtils {

  import scala.collection.JavaConverters._

  override def getSupportedPropertyDescriptors: java.util.List[PropertyDescriptor] = {
    getFilePropertiesProperties.asJava
  }

  override def getRelationships: java.util.Set[Relationship] = {
    relationships.asJava
  }

  private lazy implicit val ec = ExecutionContext.global
  private lazy implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  private lazy implicit val timer: Timer[IO] = IO.timer(ec)
  private lazy val blockingPool = Executors.newFixedThreadPool(5)
  private lazy val blocker = Blocker.liftExecutorService(blockingPool)
  private lazy val clientRef = Ref[IO].of(JavaNetClientBuilder[IO](blocker).create).unsafeRunSync()

  @OnScheduled
  def onScheduled(context: ProcessContext) = initializeClient(context, blocker, clientRef)

  override def getSupportedDynamicPropertyDescriptor(name: String): PropertyDescriptor = dynamicProperty(name)

  override def onTrigger(context: ProcessContext, session: ProcessSession) = (for {
    logger <- IO.delay(getLogger)
    flowfileEither <- getFlowFile(session, logger)
    result <- flowfileEither.flatTraverse(getFileProps(context, session, _, logger, clientRef, cs))
  } yield result).unsafeRunSync()
}