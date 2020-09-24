package com.deciphernow.greymatter.data.nifi.processors.utils

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.deciphernow.greymatter.data.nifi.http.{GmDataClient, Metadata}
import com.deciphernow.greymatter.data.nifi.properties.ListFilesProperties
import com.deciphernow.greymatter.data.nifi.relationships.ProcessorRelationships
import org.apache.nifi.processor.{ProcessContext, ProcessSession}
import org.http4s.client.Client
import org.http4s.{Headers, Uri}
import fs2.Stream
import org.apache.nifi.components.state.{Scope, StateManager}
import org.apache.nifi.context.PropertyContext
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog

import scala.util.matching.Regex

trait ListFilesStreamingFunctions extends ListFilesProperties with ProcessorRelationships with GmDataClient[IO] with ErrorHandling with ProcessorUtils {
  import scala.collection.JavaConverters._

  private def throwListFilesError[X](path: String)(either: Either[Throwable, X]) = either.leftMap(err => new Throwable(s"There was a problem listing files from ${path}: $err"))

  def streamFilesRecursively(path: Uri.Path, pathFilter: Option[Regex], relativePath: Uri.Path = "/")(implicit rootUrl: Uri, client: Client[IO], headers: Headers, cs: ContextShift[IO]): Stream[IO, Stream[IO, Either[Throwable, Metadata]]] = {
    streamFileList(path, headers).attempt.fold(Stream.emits(List[Metadata]()).attempt.covary[IO]) { (last, curr) =>
      curr.flatTraverse { metadata =>
        val newRelativePath = s"$relativePath${metadata.name}/"
        if (metadata.folder) {
          if (pathFilter.forall(_.findFirstMatchIn(newRelativePath).nonEmpty)) last ++ streamFilesRecursively(metadata.oid.get, pathFilter, newRelativePath).flatten
          else last
        }
        else last ++ Stream.eval(IO(metadata.copy(relativePath = Some(relativePath)))).attempt
      }
    }
  }

  def streamFilesFromGMData(recurse: Boolean, pathFilter: Option[Regex])(path: String)(implicit rootUrl: Uri, client: Client[IO], headers: Headers, cs: ContextShift[IO]) = {
    if (recurse) streamFilesRecursively(path, pathFilter).flatten
    else streamFileList(path, headers).filterNot(_.folder).map(e => e.copy(relativePath = Some("/"))).attempt
  }.map(throwListFilesError(s"$rootUrl/list/$path")(_).map(_.copy(rootUrlOption = Some(rootUrl.renderString))))

  def getMetadataStream(clientRef: Ref[IO, Client[IO]], inputDirectory: String)(implicit context: ProcessContext, cs: ContextShift[IO]) = for {
    client <- Stream.eval(clientRef.get)
    propertiesEither <- Stream.eval(getProperties(inputDirectory)).attempt
    filesEither <- propertiesEither.flatTraverse{ properties =>
      val filter = filterFiles(properties.fileFilter, properties.minFileAge, properties.minFileSize, properties.maxFileAge, properties.maxFileSize)(_)
      streamFilesFromGMData(properties.recurse, properties.pathFilter)(properties.path)(properties.rootUrl, client, properties.headers, cs).through(filter)
    }
  } yield filesEither

  def getProperties(inputDirectory: String)(implicit context: ProcessContext) = for {
    recurse <- IO.delay(parseRecurse)
    rootUrl <- IO.delay(parseRootUrl(rootUrlProperty))
    path <- IO.delay(parseUrlFilter).map(pathWithUrlFilter(_, inputDirectory))
    fileFilter <- IO.delay(parseFileFilter)
    pathFilter <- IO.delay(if (recurse) parsePathFilter else None)
    minFileAge <- IO.delay(parseMinFileAge)
    maxFileAge <- IO.delay(parseMaxFileAge)
    minFileSize <- IO.delay(parseMinFileSize)
    maxFileSize <- IO.delay(parseMaxFileSize)
    headers <- IO.delay(getHeaders(None))
  } yield ListFilesConfig(recurse, rootUrl, path, fileFilter, pathFilter, minFileAge, maxFileAge, minFileSize, maxFileSize, headers)

  def pathWithUrlFilter(filterOpt: Option[String], path: String) = filterOpt.map(filter => path + s"?${filter.stripPrefix("?")}").getOrElse(path)

  def filterFiles(fileFilter: Regex, minFileAge: Long, minFileSize: Long, maxFileAge: Option[Long], maxFileSize: Option[Long])(list: Stream[IO, Either[Throwable, Metadata]]) = list.filter { metadataEither =>
    metadataEither.nonEmpty && {
      val metadata = metadataEither.right.get
      (metadata.action == "C" || metadata.action == "U") &&
        fileFilter.findFirstMatchIn(metadata.name).nonEmpty &&
        metadata.getTimestamp >= minFileAge &&
        maxFileAge.forall(metadata.getTimestamp < _) &&
        metadata.getSize >= minFileSize &&
        maxFileSize.forall(metadata.getSize < _)
    }
  }

  def createAndTransferFlowFile(session: ProcessSession, logger: ComponentLog, metadata: Metadata)(implicit cs: ContextShift[IO]) = createFlowFile(session, metadata).flatMap(transferFlowfile(session)(RelSuccess, _)).attempt flatMap logTransferResult(logger)

  def createFlowFile(session: ProcessSession, metadata: Metadata) = IO.delay(session.create).flatMap(setAttributes(metadata, _, session))

  def setAttributes(metadata: Metadata, flowFile: FlowFile, session: ProcessSession) = IO.delay(session.putAllAttributes(flowFile, metadata.attributeMap.asJava))

  def getPath(context: ProcessContext): String = parseInputDirectory(context).stripPrefix("/").stripSuffix("/")

  def getMetadataStreamOrThrowError(clientRef: Ref[IO, Client[IO]])(implicit context: ProcessContext, cs: ContextShift[IO]) = for {
    inputDirectory <- Stream.eval(IO.delay(getPath(context)))
    eitherFiles = getMetadataStream(clientRef, inputDirectory)
    eitherStream <- eitherFiles.flatMap{
      case Right(metadata) => Stream.emit(metadata)
      case Left(err) => Stream.raiseError[IO](new Throwable(s"There was a problem processing metadata: $err"))
    }
  } yield eitherStream

  def getStateScope(context: PropertyContext): Scope = Scope.CLUSTER

  def getState(lastListedTimestamp: Long, stateManager: StateManager, scope: Scope, lastIdentifiers: List[String])
              (latestTimestampKey: String, idPrefix: String, justElectedPrimaryNode: Ref[IO, Boolean]) = justElectedPrimaryNode.get.flatMap { justElectedPrimary =>
    if (lastListedTimestamp == 0 || justElectedPrimary) {
      IO.delay(stateManager.getState(scope)).map {
        _.toMap.asScala.foldLeft((List[String](), 0L)) { (last, state) =>
          state match {
            case (_, value) if (Option(value).isEmpty || value.isEmpty) => last
            case (key, value) if latestTimestampKey == key => last.copy(_2 = value.toLong)
            case (key, value) if key startsWith idPrefix => last.copy(_1 = last._1 :+ value)
          }
        }
      }.flatMap { tuple => justElectedPrimaryNode.modify(old => (false, old)).map(_ => tuple) }
    }
    else IO((lastIdentifiers, lastListedTimestamp))
  }

  def saveState(latestTimestamp: Long, stateManager: StateManager, scope: Scope, timestampKey: String, identifierPrefix: String, newState: SaveState)
               (lastTimestampListed: Ref[IO, Long], lastIds: Ref[IO, List[String]]) = {
    val stateMap = Map(timestampKey -> latestTimestamp.toString) ++ newState.ids.map( identifier => identifierPrefix + "." + identifier -> identifier)
    for {
      _ <- IO.delay(stateManager.setState(stateMap.asJava, scope))
      _ <- lastTimestampListed.modify(old => (newState.timestamp, old))
      modify <- lastIds.modify(old => (newState.ids, old))
    } yield modify
  }

  def createNewSaveState[X](newTimestamp: Long, last: SaveState, id: String, count: Int)(either: Either[Throwable, X]) = either match {
    case Right(_) =>
      if (newTimestamp > last.timestamp) SaveState(List(id), newTimestamp, count)
      else if (newTimestamp == last.timestamp) last.copy(ids = last.ids :+ id, count = count)
      else last.copy(count = count)
    case Left(err) => throw new Throwable(err)
  }

  def transferAllFlowfiles(ids: List[String], timestamp: Long, session: ProcessSession, logger: ComponentLog)(stream: Stream[IO, Metadata])
                          (implicit cs: ContextShift[IO]) = stream.fold(IO(SaveState(ids, timestamp, 0))) { (lastIO, metadata) =>
    lastIO.flatMap { last =>
      val newCount = last.count + 1
      val newTimestamp = metadata.getTimestamp
      createAndTransferFlowFile(session, logger, metadata) map createNewSaveState(newTimestamp, last, metadata.getIdentifier, newCount)
    }
  }

  def listFlowfiles(context: ProcessContext, session: ProcessSession, logger: ComponentLog)
                   (stream: Stream[IO, Metadata], lastTimestampListed: Ref[IO, Long], lastIds: Ref[IO, List[String]], justElectedPrimaryNode: Ref[IO, Boolean], latestTimestampKey: String, idPrefix: String)(implicit cs: ContextShift[IO]) = for {
    lastListedTimestamp <- Stream.eval(lastTimestampListed.get)
    lastIdentifiers <- Stream.eval(lastIds.get)
    stateManager <- Stream.eval(IO.delay(context.getStateManager))
    scope <- Stream.eval(IO.delay(getStateScope(context)))
    stateTuple <- Stream.eval(getState(lastListedTimestamp, stateManager, scope, lastIdentifiers)(latestTimestampKey, idPrefix, justElectedPrimaryNode))
    (lastIdsProcessed, lastTimestamp) = stateTuple
    filteredStream = stream.filter(metadata => metadata.getTimestamp >= lastTimestamp && !lastIdsProcessed.contains(metadata.getIdentifier))
    newStateIO <- filteredStream.through(transferAllFlowfiles(lastIdentifiers, lastTimestamp, session, logger))
    newState <- Stream.eval(newStateIO)
    _ <- Stream.eval(saveState(lastTimestamp, stateManager, scope, latestTimestampKey, idPrefix, newState)(lastTimestampListed, lastIds))
    _ <- Stream.eval(IO.delay(logger.info(s"Successfully created listing with ${newState.count} new objects")))
    _ <- Stream.eval(IO.delay(session.commit()))
  } yield newState
}

case class ListFilesConfig(recurse: Boolean, rootUrl: Uri, path: String, fileFilter: Regex, pathFilter: Option[Regex], minFileAge: Long, maxFileAge: Option[Long], minFileSize: Long, maxFileSize: Option[Long], headers: Headers)

case class SaveState(ids: List[String], timestamp: Long, count: Int)