package com.deciphernow.greymatter.data.nifi.properties

import com.deciphernow.greymatter.data.nifi.http.Security
import com.deciphernow.greymatter.data.nifi.processors.utils.ErrorHandling
import io.circe.generic.auto._
import io.circe.parser.decode
import org.apache.nifi.components.{PropertyDescriptor, Validator}
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.util.StandardValidators
import org.apache.nifi.ssl.SSLContextService
import org.apache.nifi.ssl.SSLContextService.ClientAuth
import org.http4s.{Header, Headers, Uri}

import scala.collection.JavaConverters._
import scala.util.matching.Regex

trait CommonProperties extends PropertyUtils with ErrorHandling {

  protected lazy val objectPolicyProperty = buildRequiredProperty("Object Policy", "An interface (JSON) representation of a lisp like language that conveys that access constraints for retrieval of the file once stored in Grey Matter Data. This flexible policy allows for translating complex authorization schemes from a variety of systems.", List(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR), ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .defaultValue("${gmdata.objectpolicy}").build()

  protected lazy val originalObjectPolicyProperty = buildPropertyWithValidators(List(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR), "Original Object Policy", "A static string representing the original object policy from the source system. This may be a JSON structure but escaped into a string format.", ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .defaultValue("${gmdata.originalobjectpolicy}").build()

  protected lazy val securityProperty = buildPropertyWithValidators(List(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR), "Security", "A JSON representation of the security block used for user interfaces, consisting of a label, foreground, and background.", ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .defaultValue("${gmdata.security}").build()

  protected def dynamicProperty(name: String) = buildPropertyWithValidators(List(StandardValidators.ATTRIBUTE_KEY_PROPERTY_NAME_VALIDATOR), name, "", ExpressionLanguageScope.FLOWFILE_ATTRIBUTES).dynamic(true).build()

  protected def rootUrlProp(validators: List[Validator] = List(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR), scope: ExpressionLanguageScope = ExpressionLanguageScope.FLOWFILE_ATTRIBUTES) = buildRequiredProperty("Remote Url", "The RESTful endpoint for Grey Matter Data. This will be configured with the endpoint as routed through a local Grey Matter Proxy.", validators, scope).defaultValue("${gmdata.remoteurl}").build()

  protected lazy val sslContextServiceProperty = buildProperty("SSL Context Service", "The SSL Context Service used to provide client certificate information for TLS/SSL (https) connections. It is also used to connect to HTTPS Proxy.")
    .identifiesControllerService(classOf[SSLContextService]).build()

  protected lazy val attributesToSendProperty = buildPropertyWithValidators(List(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR), "Attributes to Send", "Regular expression that defines which attributes to send as HTTP headers in the request. If not defined, no attributes are sent as headers. Also any dynamic properties set will be sent as headers. The dynamic property key will be the header key and the dynamic property value will be interpreted as expression language will be the header value.", scope = ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .defaultValue("${gmdata.attributestosend}").build()

  protected def parseSSLContext(implicit context: ProcessContext) = Option(context.getProperty(sslContextServiceProperty)).flatMap { sslCont =>
    Option(sslCont.getValue).map(_ => sslCont.asControllerService(classOf[SSLContextService]).createSSLContext(ClientAuth.NONE))
  }

  protected def parseObjectPolicy(implicit context: ProcessContext, flowFile: FlowFile) = parseJson(objectPolicyProperty.getName)(parseProperty(objectPolicyProperty, Some(flowFile)))

  protected def parseOriginalObjectPolicy(implicit context: ProcessContext, flowFile: FlowFile) = parseOptionalProperty(originalObjectPolicyProperty, Some(flowFile))

  protected def parseSecurityObject(property: PropertyDescriptor)(implicit context: ProcessContext, flowFile: FlowFile) = parseOptionalProperty(property, Some(flowFile)).map { securityValue =>
    decode[Security](securityValue) match {
      case Right(value) => Right(value)
      case Left(error) => Left(new Error(s"Failed to decode the value of Security property into a valid GM Data Security Object. Error: $error"))
    }
  }

  protected def parseSecurity(implicit context: ProcessContext, flowFile: FlowFile) = parseSecurityObject(securityProperty)

  protected def parseFilename(implicit flowFile: FlowFile) = parseRequiredAttribute("filename")

  protected def parseDynamicProperties(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = context.getProperties.asScala.toMap.filter(_._1.isDynamic).map {
    case (propertyDescriptor, value) => propertyDescriptor.getName -> value
  }

  protected def parseRootUrl(rootUrlProperty: PropertyDescriptor)(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = handleErrorAndShutdown("The Remote Url property was not correctly set")(Uri.fromString(parseProperty(rootUrlProperty, flowFile)))

  protected def getHeaders(attributesToSendRegex: Option[Regex])(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = Headers((parseHeaders(attributesToSendRegex).getOrElse(Map()) ++ parseDynamicProperties).map {
    case (key, value) => Header(key, value)
  }.toList)

  protected def parseHeaders(attributesToSendRegex: Option[Regex])(implicit flowFileOpt: Option[FlowFile] = None) = for {
    attributesRegex <- attributesToSendRegex
    flowFile <- flowFileOpt
    result = flowFile.getAttributes.asScala.toMap.filter {
      case (key, _) => attributesRegex.findFirstMatchIn(key).nonEmpty
    }
  } yield result

  protected def parseAttributesToSend(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseOptionalProperty(attributesToSendProperty, flowFile)

}