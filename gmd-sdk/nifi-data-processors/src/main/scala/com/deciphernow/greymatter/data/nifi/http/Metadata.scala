package com.deciphernow.greymatter.data.nifi.http

import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.nifi.processor.util.list.ListableEntity

case class Metadata(parentoid: String,
                    name: String,
                    objectpolicy: Json,
                    mimetype: Option[String],
                    size: Option[Long],
                    action: String,
                    security: Option[Security],
                    originalObjectPolicy: Option[String],
                    custom: Option[Json],
                    oid: Option[String],
                    tstamp: Option[String],
                    relativePath: Option[String],
                    policy: Option[Policy],
                    isdir: Option[Boolean],
                    isfile: Option[Boolean] = Some(true),
                    allowPartialPermissions: Option[Boolean] = None,
                    sha256plain: Option[String] = None,
                    rootUrlOption: Option[String] = None) extends ListableEntity {

  lazy val folder = isfile.isEmpty || !isfile.get || (isdir.nonEmpty && isdir.get)

  def attributeMap = Map(
    "filename" -> Option(name),
    "file.owner" -> fileOwner,
    "path" -> relativePath,
    "file.size" -> size.map(_.toString),
    "file.lastModifiedTime" -> tstamp,
    "file.creationTime" -> tstamp,
    "mime.type" -> mimetype,
    "gmdata.fileurl" -> oid.flatMap(id => rootUrlOption.map(rootUrl => s"$rootUrl/stream/$id")),
    "gmdata.oid" -> oid,
    "gmdata.parentoid" -> Option(parentoid),
    "gmdata.objectpolicy" -> Option(objectpolicy.noSpaces),
    "gmdata.originalobjectpolicy" -> originalObjectPolicy,
    "gmdata.security" -> security.map(_.asJson.noSpaces),
    "gmdata.custom" -> custom.map(_.noSpaces),
    "gmdata.sha256" -> sha256plain).filterNot(_._2.isEmpty).mapValues(_.get)

  def fileOwner = objectpolicy.as[ObjectPolicy].toOption.flatMap(objPolicy => getFileOwner(objPolicy.requirements))

  private def getInnerFileOwner(requirements: Requirements): Option[String] = {
    requirements.f.flatMap { f =>
      if (f == "contains") requirements.a.flatMap(_(1).v)
      else requirements.a.flatMap { innerReqs =>
        innerReqs.foldLeft(None: Option[String]) {
          case (_@ None, curr) => getInnerFileOwner(curr)
          case (last, _) => last
        }
      }
    }
  }

  private def getFileOwner(requirements: Requirements, lastRequirements: Option[Requirements] = None): Option[String] = {
    val cruxpMap = "CRUDXP".map(letter => Requirements(v = Some(letter.toString))).toList.sortBy(_.v)
    val successfulRequirements = List(Requirements(Some("yield"), Some(cruxpMap)), Requirements(Some("yield-all")))
    val sortedRequirements = requirements.copy(a = requirements.a.map(_.sortBy(_.v)))
    if (successfulRequirements.contains(sortedRequirements)) lastRequirements.flatMap(getInnerFileOwner)
    else requirements.a.flatMap {
      _.foldLeft((None, None): (Option[Requirements], Option[String])) {
        case ((last, _@ None), curr) => (Some(curr), getFileOwner(curr, last))
        case (owner, _) => owner
      }._2
    }
  }

  def getName = name

  def getIdentifier = oid.get

  def getTimestamp = java.lang.Long.valueOf(tstamp.get, 16)

  def getSize = size.getOrElse(0)
}

case class ObjectPolicy(label: String, requirements: Requirements)

case class Requirements(f: Option[String] = None, a: Option[List[Requirements]] = None, v: Option[String] = None)

case class Security(label: String, foreground: String, background: String)

case class Policy(policy: List[String] = List("C", "R", "U", "D", "X", "P"))