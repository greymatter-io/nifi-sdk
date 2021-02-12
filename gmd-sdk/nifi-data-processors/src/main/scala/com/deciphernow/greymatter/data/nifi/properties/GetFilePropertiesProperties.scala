package com.deciphernow.greymatter.data.nifi.properties

import com.deciphernow.greymatter.data.nifi.processors.utils.ErrorHandling
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.ProcessContext

trait GetFilePropertiesProperties extends CommonProperties with ErrorHandling {

  lazy val rootUrlProperty = rootUrlProp()

  lazy val getFilePropertiesProperties = List(rootUrlProperty, sslContextServiceProperty, httpTimeoutProperty, attributesToSendProperty)

  def parseFilePath(implicit context: ProcessContext, flowFile: FlowFile) = parseRequiredAttribute("path").stripPrefix("/").stripSuffix("/")

  def parseFileName(implicit context: ProcessContext, flowFile: FlowFile) = parseRequiredAttribute("filename")
}