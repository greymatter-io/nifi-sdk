package com.deciphernow.greymatter.data.nifi.properties

import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.ProcessContext
import com.deciphernow.greymatter.data.nifi.processors.utils.ErrorHandling
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.processor.util.list.AbstractListProcessor._

trait ListFilesProperties extends CommonProperties with ErrorHandling {

  protected lazy val inputDirectoryProperty = buildRequiredProperty("Input Directory", "The input directory from which files to pull files.").build()

  protected lazy val recurseProperty = buildRequiredProperty("Recurse Subdirectories", "Indicates whether to list files from subdirectories of the directory.").defaultValue("true").allowableValues("true", "false").build()

  protected lazy val urlFilterProperty = buildProperty("URL Filter Argument", "When present, this will be added as querystring arguments for requests to the /list call. Supported querystring keys are childCount, count, last, and tstamp.").build()

  protected lazy val fileFilterProperty = buildRequiredProperty("File Filter", "Only files whose names match the given regular expression will be picked up.").defaultValue("""[^\.].*""").build()

  protected lazy val pathFilterProperty = buildProperty("Path Filter", "When Recurse Subdirectories is true, then only subdirectories whose path matches the given regular expression will be scanned").build()

  protected lazy val minFileAgeProperty = buildRequiredProperty("Minimum File Age", "The minimum age,in seconds, that a file must be in order to be pulled; any file younger than this amount of time (according to last modification date) will be ignored").defaultValue("0").build()

  protected lazy val maxFileAgeProperty = buildProperty("Maximum File Age", "The maximum age, in seconds, that a file must be in order to be pulled; any file older than this amount of time (according to last modification date) will be ignored").build()

  protected lazy val minFileSizeProperty = buildRequiredProperty("Minimum File Size", "The minimum size, in bytes, that a file must be in order to be pulled").defaultValue("0").build()

  protected lazy val maxFileSizeProperty = buildProperty("Maximum File Size", "The maximum size, in bytes, that a file must be in order to be pulled").build()

  protected lazy val rootUrlProperty = rootUrlProp(List(), ExpressionLanguageScope.NONE)

  protected lazy val listFilesProperties = List(rootUrlProperty, sslContextServiceProperty, inputDirectoryProperty, recurseProperty, urlFilterProperty, fileFilterProperty, pathFilterProperty, minFileAgeProperty, maxFileAgeProperty, minFileSizeProperty, maxFileSizeProperty, DISTRIBUTED_CACHE_SERVICE, LISTING_STRATEGY, TARGET_SYSTEM_TIMESTAMP_PRECISION)

  protected def parseInputDirectory(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseProperty(inputDirectoryProperty)

  protected def parseRecurse(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseProperty(recurseProperty).toBoolean

  protected def parseUrlFilter(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseOptionalProperty(urlFilterProperty)

  protected def parseFileFilter(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseProperty(fileFilterProperty).r

  protected def parsePathFilter(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseOptionalProperty(pathFilterProperty).map(_.r)

  protected def parseMinFileAge(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseProperty(minFileAgeProperty).toLong * 1000000000

  protected def parseMaxFileAge(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseOptionalProperty(maxFileAgeProperty).map(_.toLong * 1000000000)

  protected def parseMinFileSize(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseProperty(minFileSizeProperty).toLong

  protected def parseMaxFileSize(implicit context: ProcessContext, flowFile: Option[FlowFile] = None) = parseOptionalProperty(maxFileSizeProperty).map(_.toLong)
}