package com.deciphernow.greymatter.data.nifi.properties

import io.circe.parser.parse
import org.apache.nifi.components.{PropertyDescriptor, Validator}
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.util.StandardValidators

trait PropertyUtils {

  protected def buildProperty(name: String, description: String, scope: ExpressionLanguageScope = ExpressionLanguageScope.NONE, required: Boolean = false) =
    new PropertyDescriptor.Builder()
      .name(name)
      .description(description)
      .required(required)
      .expressionLanguageSupported(scope)
      .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)

  protected def buildPropertyWithValidators(validators: List[Validator], name: String, description: String, scope: ExpressionLanguageScope = ExpressionLanguageScope.NONE, required: Boolean = false) = {
    validators.foldLeft(buildProperty(name: String, description: String, scope, required)) { (propertyBuilder, validator) =>
      propertyBuilder.addValidator(validator)
    }
  }

  protected def buildRequiredProperty(name: String, description: String, validators: List[Validator] = List(), scope: ExpressionLanguageScope = ExpressionLanguageScope.NONE) = buildPropertyWithValidators(validators, name, description, scope, required = true)

  protected def parseProperty(property: PropertyDescriptor, flowFileOpt: Option[FlowFile] = None)(implicit context: ProcessContext) =
    flowFileOpt.map(context.getProperty(property).evaluateAttributeExpressions(_)).getOrElse(context.getProperty(property)).getValue

  protected def parseOptionalProperty(property: PropertyDescriptor, flowFileOpt: Option[FlowFile] = None)(implicit context: ProcessContext) = Option(parseProperty(property, flowFileOpt))
    .flatMap(prop => if (prop.isEmpty) None else Some(prop))

  protected def parseJson(propertyName: String)(propertyValue: String) = {
    parse(propertyValue) match {
      case Right(value) => Right(value)
      case Left(error) => Left(new Error(s"Failed to decode the value of $propertyName into valid JSON. Error: $error"))
    }
  }

  protected def parseRequiredAttribute(attribute: String)(implicit flowFile: FlowFile) =
    parseAttribute(attribute).getOrElse(throw new Error(s"Flowfile is missing attribute: $attribute"))

  protected def parseAttribute(attribute: String)(implicit flowFile: FlowFile) =
    Option(flowFile.getAttribute(attribute))

}
