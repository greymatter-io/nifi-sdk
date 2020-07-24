package com.deciphernow.greymatter.data.nifi.http

import io.circe.{Decoder, Printer}
import io.circe.fs2._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Headers, Method, Request, Response, Uri}
import org.http4s.multipart.{Multipart, Part}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode
import cats.implicits._
import fs2.Stream
import cats.effect.Sync
import org.http4s.Status.{ServerError, Successful}

trait GmDataClient[F[_]] extends Http4sClientDsl[F] {

  implicit val securityDecoder = Decoder[Security].either(Decoder[Map[String, String]]).map(_.left.toOption)

  def writeToGmData[X](client: Client[F], headers: Headers, existingRequest: F[Request[F]])(implicit decoder: Decoder[X], F: Sync[F]): F[X] = {
    val request = existingRequest.map(_.withHeaders(headers))
    client.fetch(request) {
      case Successful(resp) => resp.as[String].map { response =>
        decode[X](response) match {
          case Right(some) => some
          case Left(err) => throw new Throwable(s"There was a problem decoding $response: $err")
        }
      }
      case ServerError(resp) => resp.as[String].map(err => throw new Throwable(s"There was an error with a call to GM Data: $err"))
      case errResponse => errResponse.as[String].map(err => throw new Throwable(s"There was an error with a call to GM Data: $err"))
    }
  }

  def streamFromGMData[X](client: Client[F], headers: Headers, existingRequest: F[Request[F]])(implicit F: Sync[F], d: Decoder[X]): Stream[F, X] = for {
    request <- Stream.eval(existingRequest.map(_.withHeaders(headers)))
    stream <- client.stream(request).flatMap {
      case Successful(resp: Response[F]) => resp.body.through(byteArrayParser).through(decoder[F, X])
      case errResponse => Stream.eval(errResponse.as[String].map(err => throw new Throwable(s"There was an error with a call to GM Data: $err")))
    }
  } yield stream

  private def get[X](path: Uri)(client: Client[F], headers: Headers)(implicit F: Sync[F], decoder: Decoder[X]): F[X] = writeToGmData[X](client, headers, Method.GET(path))

  protected def getSelf(rootUrl: Uri, client: Client[F], headers: Headers)(implicit F: Sync[F]): F[SelfResponse] = get(rootUrl / "self")(client: Client[F], headers: Headers)

  protected def getConfig(rootUrl: Uri, client: Client[F], headers: Headers)(implicit F: Sync[F]): F[Config] = get(rootUrl / "config")(client: Client[F], headers: Headers)

  private def getProps[X](path: Uri.Path, headers: Headers)(implicit rootUrl: Uri, client: Client[F], decoder: Decoder[X], F: Sync[F]): F[X] = get(rootUrl / "props" / path)(client: Client[F], headers: Headers)

  protected def getFolderProps(path: Uri.Path, headers: Headers)(implicit rootUrl: Uri, client: Client[F], F: Sync[F]) = getProps[Metadata](path, headers)

  private def getList[X](path: Uri.Path, headers: Headers)(implicit rootUrl: Uri, client: Client[F], decoder: Decoder[X], F: Sync[F]): F[List[X]] = get(parseUrl(s"$rootUrl/list/$path"))(client: Client[F], headers: Headers)

  protected def getFileList(path: Uri.Path, headers: Headers)(implicit rootUrl: Uri, client: Client[F], F: Sync[F]) = getList[Metadata](path, headers)

  protected def streamFileList(path: Uri.Path, headers: Headers)(implicit rootUrl: Uri, client: Client[F], F: Sync[F]) = streamFromGMData[Metadata](client, headers, Method.GET(parseUrl(s"$rootUrl/list/$path")))

  protected def writeFolder(metadata: List[Metadata], rootUrl: Uri, headers: Headers)(implicit client: Client[F], F: Sync[F]) = {
    val printer = Printer.spaces2.copy(dropNullValues = true)
    val body = metadata.asJson.pretty(printer)
    val multipart = Multipart[F](Vector(Part.formData("meta", body)))
    val request = Method.POST(multipart, rootUrl / "write")
    writeToGmData[List[Metadata]](client, multipart.headers ++ headers, request).map(_.head)
  }

  private def parseUrl(string: String) = Uri.fromString(string) match {
    case Right(url) => url
    case Left(err) => throw new Throwable(s"$string is an invalid URL: $err")
  }
}

case class Config(GMDATA_NAMESPACE_OID: String, GMDATA_NAMESPACE_USERFIELD: String)

case class SelfResponse(values: Map[String, Option[List[String]]]) {
  val getUserField = (userField: String) => values.mapValues(_.map(_.head))(userField).toRight(new Throwable(s"/self did not return a value for $userField"))
}

