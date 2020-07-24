package com.deciphernow.greymatter.data.nifi.processors
import java.util.concurrent.Executors

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ContextShift, IO}
import com.deciphernow.greymatter.data.nifi.processors.utils.{ListFilesStreamingFunctions, SaveState}
import org.apache.nifi.annotation.behavior.{DynamicProperty, Stateful}
import org.apache.nifi.components.state.Scope
import org.apache.nifi.expression.ExpressionLanguageScope
import org.http4s.client.JavaNetClientBuilder

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import fs2.Stream
import org.apache.nifi.annotation.notification.{OnPrimaryNodeStateChange, PrimaryNodeState}

// NiFi
import org.apache.nifi.annotation.documentation.{ CapabilityDescription, Tags }
import org.apache.nifi.annotation.lifecycle.OnScheduled
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.processor._

@Tags(Array("gmdata"))
@CapabilityDescription("Retrieves a listing of files from a Grey Matter Data instance. For each file that is listed, creates a FlowFile that represents the file.")
@DynamicProperty(name = "Header Name", value = "Attribute Expression Language", expressionLanguageScope = ExpressionLanguageScope.FLOWFILE_ATTRIBUTES, description = "Send request header with a key matching the Dynamic Property Key and a value created by evaluating the Attribute Expression Language set in the value of the Dynamic Property.")
@Stateful(scopes = Array(Scope.CLUSTER), description = "After performing a listing of GM Data Files, the timestamp of the last modified file is stored. This allows the Processor to list only files that have been added or modified after this date the next time that the Processor is run")
class ListFiles extends AbstractProcessor with ListFilesStreamingFunctions {
  import scala.collection.JavaConverters._

  private lazy implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  private lazy implicit val ctxShift: ContextShift[IO] = IO.contextShift(ec)
  private lazy val blockingPool = Executors.newFixedThreadPool(5)
  private lazy val blocker = Blocker.liftExecutorService(blockingPool)
  private lazy val clientRef = Ref[IO].of(JavaNetClientBuilder[IO](blocker).create).unsafeRunSync()
  lazy val lastTimestampListed: Ref[IO, Long] = Ref[IO].of(0L).unsafeRunSync()
  lazy val justElectedPrimaryNode: Ref[IO, Boolean] = Ref[IO].of(false).unsafeRunSync()
  lazy val lastIds: Ref[IO, List[String]] = Ref[IO].of(List[String]()).unsafeRunSync()
  lazy val resetState: Ref[IO, Boolean] = Ref[IO].of(false).unsafeRunSync()
  lazy val lastTimestampKey = "listing.timestamp"
  lazy val idPrefix = "id"

  override def getSupportedPropertyDescriptors: java.util.List[PropertyDescriptor] = {
    listFilesProperties.asJava
  }

  override def getRelationships: java.util.Set[Relationship] = {
    relationships.asJava
  }

  @OnScheduled
  def onScheduled(context: ProcessContext) = initializeClient(context, blocker, clientRef)

  override def getSupportedDynamicPropertyDescriptor(name: String): PropertyDescriptor = dynamicProperty(name)

  override def onTrigger(context: ProcessContext, session: ProcessSession) = {
    for{
      logger <- Stream.eval(IO.delay(getLogger))
      stream = getMetadataStreamOrThrowError(clientRef)(context, ctxShift)
      listed <- listFlowfiles(context, session, getLogger)(stream, lastTimestampListed, lastIds, justElectedPrimaryNode, lastTimestampKey, idPrefix).attempt
      _ = logErrors(logger, { newState: SaveState => s"Successfully listed ${newState.count} files"}, "Failed to list files")(listed)
    } yield listed
  }.compile.drain.unsafeRunSync()

  def resetTime = for {
    _ <- lastTimestampListed.modify(old => (0, old))
    modify <- lastIds.modify(old => (List(), old)).flatMap(_ => IO.unit)
  } yield modify

  def setResetState(bool: Boolean) = resetState.modify(old => (bool, old)).flatMap(_ => IO.unit)

  @OnScheduled
  def resetStateIfNecessary(context: ProcessContext): Unit = {
    for {
      reset <- resetState.get
      lastTimestamp <- lastTimestampListed.get
      stateMap <- IO.delay(context.getStateManager.getState(getStateScope(context)))
      _ <- if (lastTimestamp != 0 && !stateMap.toMap.asScala.contains(lastTimestampKey)) resetTime else IO.unit
      _ <- if(reset) IO.delay(context.getStateManager.clear(getStateScope(context))).flatMap( _ => setResetState(false))
      else IO.unit
    } yield stateMap
  }.unsafeRunSync()

  override def onPropertyModified(descriptor: PropertyDescriptor, oldValue: String, newValue: String): Unit = {
    if (isConfigurationRestored) resetTime.flatMap(_ => setResetState(true)) else IO.unit
  }.unsafeRunSync()

  @OnPrimaryNodeStateChange
  def onPrimaryNodeChange(newState: PrimaryNodeState): Unit = justElectedPrimaryNode.modify(old => (newState == PrimaryNodeState.ELECTED_PRIMARY_NODE, old)).unsafeRunSync()
}