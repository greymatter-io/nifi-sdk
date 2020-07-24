package com.deciphernow.greymatter.data.nifi.processors

import java.util.concurrent.Executors

import cats.effect.concurrent.Ref
import cats.effect.{ Blocker, ContextShift, IO }
import cats.implicits._
import com.deciphernow.greymatter.data.nifi.processors.utils.GetOidForPathStreamingFunctions
import org.apache.nifi.annotation.behavior.DynamicProperty
import org.apache.nifi.expression.ExpressionLanguageScope
import org.http4s.client.JavaNetClientBuilder

import scala.concurrent.ExecutionContext

// NiFi
import org.apache.nifi.annotation.behavior.{ ReadsAttribute, ReadsAttributes }
import org.apache.nifi.annotation.documentation.{ CapabilityDescription, SeeAlso, Tags }
import org.apache.nifi.annotation.lifecycle.OnScheduled
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.processor._

@Tags(Array("gmdata"))
@CapabilityDescription("A processor that creates a folder structure in GM Data based on a given path.")
@SeeAlso(Array())
@DynamicProperty(name = "Header Name", value = "Attribute Expression Language", expressionLanguageScope = ExpressionLanguageScope.FLOWFILE_ATTRIBUTES, description = "Send request header with a key matching the Dynamic Property Key and a value created by evaluating the Attribute Expression Language set in the value of the Dynamic Property.")
@ReadsAttributes(Array(
  new ReadsAttribute(attribute = "filename", description = """The path and filename. Only folders in the path will be created. E.g. a value of "X" would result in no folders being created, but for "X/Y" or "X/Y.pdf", a folder named "X" would be created.""")))
class GetOidForPath extends AbstractProcessor with GetOidForPathStreamingFunctions {

  import scala.collection.JavaConverters._

  protected[this] override def init(context: ProcessorInitializationContext): Unit = {
  }

  override def getSupportedPropertyDescriptors: java.util.List[PropertyDescriptor] = {
    getOidForPathProperties.asJava
  }

  override def getRelationships: java.util.Set[Relationship] = {
    relationships.asJava
  }

  private lazy implicit val ec = ExecutionContext.global
  private lazy implicit val ctxShift: ContextShift[IO] = IO.contextShift(ec)
  private lazy val blockingPool = Executors.newFixedThreadPool(5)
  private lazy val blocker = Blocker.liftExecutorService(blockingPool)
  private lazy val clientRef = Ref[IO].of(JavaNetClientBuilder[IO](blocker).create).unsafeRunSync()

  @OnScheduled
  def onScheduled(context: ProcessContext) = initializeClient(context, blocker, clientRef)

  override def getSupportedDynamicPropertyDescriptor(name: String): PropertyDescriptor = dynamicProperty(name)
  override def onTrigger(context: ProcessContext, session: ProcessSession) = {
    for{
      logger <- IO.delay(getLogger)
      flowFileEither <- getFlowFile(session, logger)
      result <- flowFileEither.flatTraverse(startProcessing(logger)(context, session, _, clientRef, ctxShift))
    } yield result

  }.unsafeRunSync()
}

