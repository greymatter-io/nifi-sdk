package com.deciphernow.greymatter.data.nifi.properties

import cats.effect.IO
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.ProcessContext
import org.http4s._
import cats.implicits._
import com.deciphernow.greymatter.data.nifi.http.{ Metadata, Security }
import com.deciphernow.greymatter.data.nifi.processors.utils.ErrorHandling
import io.circe.Json
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.processor.util.StandardValidators

trait GetOidForPathProperties extends CommonProperties with ErrorHandling {

  protected lazy val userfieldObjectPolicyProperty = buildProperty("Userfield Folder Object Policy", "When provided, this is an override object policy to be assigned to the created userfield folder if the folder does not yet exist.").build()

  protected lazy val userfieldOriginalObjectPolicyProperty = buildProperty("Userfield Folder Original Object Policy", "When provided, this is an override original object policy to be assigned to the created userfield folder if the folder does not yet exist.").build()

  protected lazy val userfieldSecurityProperty = buildPropertyWithValidators(List(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR), "Userfield Folder Security", "An interface (JSON) representation of the security block used for user interfaces, consisting of a label, foreground, and background that should be applied when creating the userfield folder.", ExpressionLanguageScope.FLOWFILE_ATTRIBUTES).build()

  protected lazy val intermediatePrefixProperty = buildProperty("Intermediate Folder Prefix", "When provided, this path indicates intermediate folders that should exist between the userfield folder, and the folders passed in").build()

  protected lazy val intermediateObjectPolicyProperty = buildProperty("Intermediate Folder Object Policy", "When provided, this is an override policy to be assigned to any created intermediate folders as represented by the Intermediate Folder Prefix").build()

  protected lazy val intermediateOriginalObjectPolicyProperty = buildProperty("Intermediate Folder Original Object Policy", "When provided, this is an override original object policy to be assigned to the created intermediate folder if the folder does not yet exist.").build()

  protected lazy val intermediateSecurityProperty = buildPropertyWithValidators(List(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR), "Intermediate Folder Security", "An interface (JSON) representation of the security block used for user interfaces, consisting of a label, foreground, and background that should be applied when creating intermediate folders that prefix the provided filename path.", ExpressionLanguageScope.FLOWFILE_ATTRIBUTES).build()

  protected lazy val getOidForPathProperties = List(objectPolicyProperty, originalObjectPolicyProperty, securityProperty, rootUrlProperty, sslContextServiceProperty, userfieldObjectPolicyProperty, userfieldOriginalObjectPolicyProperty, userfieldSecurityProperty, intermediatePrefixProperty, intermediateObjectPolicyProperty, intermediateOriginalObjectPolicyProperty, intermediateSecurityProperty, attributesToSendProperty)

  protected def parseUserfieldSecurity(implicit context: ProcessContext, flowFile: FlowFile) = parseSecurityObject(userfieldSecurityProperty)

  protected def parseUserfieldObjectPolicy(implicit context: ProcessContext, flowFile: FlowFile) = parseOptionalProperty(userfieldObjectPolicyProperty, None).map(parseJson(userfieldObjectPolicyProperty.getName))

  protected def parseUserfieldOriginalObjectPolicy(implicit context: ProcessContext, flowFile: FlowFile) = parseOptionalProperty(userfieldOriginalObjectPolicyProperty, None)

  protected def parseIntermediatePrefix(implicit context: ProcessContext, flowFile: FlowFile) = parseOptionalProperty(intermediatePrefixProperty, None)

  protected def parseIntermediateSecurity(implicit context: ProcessContext, flowFile: FlowFile) = parseSecurityObject(intermediateSecurityProperty)

  protected def parseIntermediateObjectPolicy(implicit context: ProcessContext, flowFile: FlowFile) = parseOptionalProperty(intermediateObjectPolicyProperty, None).map(parseJson(intermediateObjectPolicyProperty.getName))

  protected def parseIntermediateOriginalObjectPolicy(implicit context: ProcessContext, flowFile: FlowFile) = parseOptionalProperty(intermediateOriginalObjectPolicyProperty, None)

  protected def parsePath(implicit flowFile: FlowFile) = parseRequiredAttribute("path")

  private def splitIntoFolders(path: String) = path.split("/").filter(name => name.nonEmpty && name != ".").toList

  protected def getPropertyConfig(implicit context: ProcessContext, flowFile: FlowFile) = for {
    rootUrl <- IO.delay(parseRootUrl)
    folders <- IO.delay(parsePath).attempt.map(_.map(splitIntoFolders)) map handleErrorAndContinue(description = "The path attribute was not able to be parsed from the flowfile")
    objectPolicy <- IO.fromEither(parseObjectPolicy).attempt.map(handleErrorAndShutdown("The Object Policy property was not correctly set"))
    security <- IO.delay(parseSecurity) map getOptionalProperty("The Security property was not correctly set")
    originalObjectPolicy <- IO.delay(parseOriginalObjectPolicy)
    userfieldObjectPolicy <- IO.delay(parseUserfieldObjectPolicy) map getOptionalProperty("The Userfield Object Policy was not correctly set")
    userfieldSecurity <- IO.delay(parseUserfieldSecurity) map getOptionalProperty("The Userfield Security property was not correctly set")
    userfieldOriginalObjectPolicy <- IO.delay(parseUserfieldOriginalObjectPolicy)
    intermediatePrefix <- IO.delay(parseIntermediatePrefix) map (_.map(splitIntoFolders))
    intermediateObjectPolicy <- IO.delay(parseIntermediateObjectPolicy) map getOptionalProperty("The Intermediate Folder Object Policy was not correctly set")
    intermediateSecurity <- IO.delay(parseIntermediateSecurity) map getOptionalProperty("The Intermediate Folder Security property was not correctly set")
    intermediateOriginalObjectPolicy <- IO.delay(parseIntermediateOriginalObjectPolicy)
    attributesToSendRegex <- IO.delay(parseAttributesToSend.map(_.r))
    headers <- IO.delay(getHeaders(attributesToSendRegex)(context, Some(flowFile)))
    config = GetOidForPathConfig(rootUrl, folders, objectPolicy, security, originalObjectPolicy, userfieldObjectPolicy, userfieldSecurity, userfieldOriginalObjectPolicy, intermediatePrefix, intermediateSecurity: Option[Security], intermediateObjectPolicy, intermediateOriginalObjectPolicy, headers)
  } yield config

}

case class GetOidForPathConfig(rootUrl: Uri,
    folders: Either[Throwable, List[String]],
    objectPolicy: Json,
    security: Option[Security],
    originalObjectPolicy: Option[String],
    userFieldObjectPolicy: Option[Json],
    userFieldSecurity: Option[Security],
    userFieldOriginalObjectPolicy: Option[String],
    intermediateFolders: Option[List[String]],
    intermediateSecurity: Option[Security],
    intermediateObjectPolicy: Option[Json],
    intermediateOriginalObjectPolicy: Option[String],
    headers: Headers) {

  def metadata(parentoid: String, name: String, objectPolicy: Json = objectPolicy, originalObjectPolicy: Option[String] = originalObjectPolicy, security: Option[Security] = security) =
    Metadata(parentoid, name, objectPolicy, None, None, "C", security, originalObjectPolicy, None, None, None, None, None, None, None)

  lazy val userMetadata = fillMetadata(userFieldObjectPolicy, userFieldOriginalObjectPolicy, userFieldSecurity)

  lazy val intermediateMetadata = fillMetadata(intermediateObjectPolicy, intermediateOriginalObjectPolicy, intermediateSecurity)

  private lazy val fillMetadata = (objPolicyOption: Option[Json], origObjPolicyOption: Option[String], securityOption: Option[Security]) => (parentoid: String, name: String) => {
    val objPolicy = objPolicyOption.getOrElse(objectPolicy)
    val origObjPolicy = if (origObjPolicyOption.isEmpty) originalObjectPolicy else origObjPolicyOption
    val sec = if (securityOption.isEmpty) security else securityOption
    metadata(parentoid, name, objPolicy, origObjPolicy, sec)
  }
}