package com.deciphernow.greymatter.data.nifi.properties

import com.deciphernow.greymatter.data.{ Action, ParentOid, Size }
import io.circe.Json
import org.apache.nifi.components.AllowableValue
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.util.StandardValidators

import scala.util.Try

trait PrepareWriteRequestProperties extends CommonProperties {

  protected lazy val oidProperty = buildRequiredProperty("Folder Object ID", "A string representing the parent object identifier that acts as a reference to the folder item in Grey Matter Data that should enclose this file", List(StandardValidators.ATTRIBUTE_EXPRESSION_LANGUAGE_VALIDATOR), ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
    .defaultValue("${gmdata.parentoid}").build()

  protected lazy val actionProperty = {
    val actionValues = List("C", "R", "U", "D", "P", "X")
    buildProperty("Action", "A string denoting the action for the event that will be prepared")
      .allowableValues(actionValues: _*)
      .defaultValue(actionValues.head)
      .build()
  }

  protected lazy val customProperty = buildProperty("Custom", "A JSON structure containing custom fields.").build()

  protected lazy val prepareWriteRequestProperties = List(objectPolicyProperty, oidProperty, originalObjectPolicyProperty, securityProperty, actionProperty, customProperty)

  protected def parseParentOid(implicit context: ProcessContext, flowFile: FlowFile) = ParentOid.unsafeFrom {
    parseProperty(oidProperty, Some(flowFile))
  }

  protected def parseAction(implicit context: ProcessContext, flowFile: FlowFile) = Try {
    Action.unsafeFrom {
      parseProperty(actionProperty)
    }
  }.getOrElse(Action.unsafeFrom("C"))

  protected def parseCustom(implicit context: ProcessContext, flowFile: FlowFile) = parseOptionalProperty(customProperty, None) map parseJson(customProperty.getName)

  protected def parseMimeType(implicit flowFile: FlowFile) = parseRequiredAttribute("mime.type")

  protected def parseSize(implicit flowFile: FlowFile) = Try {
    Size.unsafeFrom(flowFile.getAttribute("file.size").toLong).toString
  }.getOrElse("0")

  protected def parseChunkSize(implicit flowFile: FlowFile) = parseAttribute("chunk.size").map(_.toInt).getOrElse(10000)
}

