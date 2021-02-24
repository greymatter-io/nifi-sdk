/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.deciphernow.greymatter.data.nifi.processors

import java.io.{ByteArrayInputStream, File}

import cats.effect.{ContextShift, IO, Timer}
import com.deciphernow.greymatter.data.nifi.http.{Metadata, Security}
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._
import com.deciphernow.greymatter.data.{Action, TestContext}
import com.deciphernow.greymatter.data.nifi.properties.PrepareWriteRequestProperties
import com.deciphernow.greymatter.data.nifi.relationships.ProcessorRelationships
import io.circe.{Json, Printer}
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.util.{MockFlowFile, TestRunner, TestRunners}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.headers.{`Content-Type`, `Transfer-Encoding`}
import org.http4s.{Header, Headers, MediaType, Method, TransferCoding, Uri}
import org.scalatest._

import scala.concurrent.ExecutionContext

class PrepareWriteRequestTest extends FunSpec with TestContext with ProcessorRelationships with PrepareWriteRequestProperties with Matchers {

  import scala.collection.JavaConverters._

  val optionalProperties = commonOptionalProperties ++ Map((actionProperty, "U"), (customProperty, """{"custom1":"something","custom2":"something"}"""))
  val propertiesWithoutSecurity = optionalProperties.filter(_._1 != securityProperty)
  implicit lazy val ec = ExecutionContext.global
  implicit val ctxShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  def metadatas(securityOption: Option[Security], parentOid: String)(implicit objectPolicy: Json) = {
    val security = decode[Security](optionalProperties(securityProperty)).toOption
    val origObjectPolicyProperty = Some(optionalProperties(originalObjectPolicyProperty))
    val custom = parse(optionalProperties(customProperty)).toOption
    List(metadata(objectPolicy, "0", Action.unsafeFrom("C"), securityOption, None, None, parentOid),
      metadata(objectPolicy, "12", Action.unsafeFrom("U"), security, origObjectPolicyProperty, custom, parentOid))
  }
  private val metadata = (objectPolicy: Json, size: String, action: Action, security: Option[Security], originalObjectPolicy: Option[String], custom: Option[Json], parentOid: String) => {
    val filename = "mycontext.txt"
    val mimeType = Some("text/plain")
    Metadata(parentOid, filename, objectPolicy, mimeType, Some(size.toLong), action.value, security, originalObjectPolicy, custom, None, None, None, None, None)
  }

  def sizeMap(size: String)(implicit runner: TestRunner, properties: Map[PropertyDescriptor, String]) = if (size == "0") Map() else {
    properties.foreach { case (property, value) => runner.setProperty(property, value) }
    Map("file.size" -> size)
  }

  def halfAttributeMap(implicit metadata: Metadata, runner: TestRunner) = Map("filename" -> metadata.name, "chunk.size" -> "20000")

  def attributeMap(implicit metadata: Metadata, runner: TestRunner, properties: Map[PropertyDescriptor, String] = optionalProperties) =
    halfAttributeMap ++ Map("mime.type" -> metadata.mimetype.get) ++ sizeMap(metadata.size.get.toString)

  val updatedContent = (originalContent: String, boundary: String, metadata: String, fileName: String) =>
    s"""|
        |$boundary
        |Content-Disposition: form-data; name="metadata"
        |
        |[
        |
        |$metadata
        |]
        |
        |$boundary
        |Content-Disposition: form-data; name="file"; filename="$fileName"
        |
        |$originalContent
        |$boundary--
        |
        |""".stripMargin

  private def runProcessorTests(getAttributes: (Metadata, TestRunner) => Map[String, String], securityOption: Option[Security] = None, enqueue: Boolean = true, parentOid: String = "1234123412341234")(runnerTests: (TestRunner, Metadata, String) => Unit)(setProperties: (TestRunner, Metadata) => Unit = { (runner, metadata) =>
    runner.setProperty(oidProperty, metadata.parentoid)
  }) = {
    val someContent = "some content"
    val processor = new PrepareWriteRequest()
    policies.foreach { workingObjectPolicy =>
      implicit val policyJson = parse(workingObjectPolicy).right.get
      metadatas(securityOption, parentOid).foreach { implicit metadata =>
        implicit val runner = TestRunners.newTestRunner(processor)
        setProperties(runner, metadata)
        runner.setProperty(objectPolicyProperty, workingObjectPolicy)
        val content = new ByteArrayInputStream(someContent.getBytes)
        if(enqueue) runner.enqueue(content, getAttributes(metadata, runner).asJava)
        runner.run(1)
        runnerTests(runner, metadata, someContent)
      }
    }
  }

  val happyPathTest = { (runner: TestRunner, metadata: Metadata, content: String) =>
    runner.assertTransferCount(RelSuccess, 1)
    runner.assertTransferCount(RelFailure, 0)
    val metadataString = metadata.asJson.pretty(Printer.noSpaces.copy(dropNullValues = true))
    for (flowFile: MockFlowFile <- runner.getFlowFilesForRelationship(RelSuccess).asScala) {
      val boundary = new String(flowFile.toByteArray).lines.toList(1).split("--")(1)
      flowFile.assertAttributeEquals("mime.type", s"multipart/form-data; boundary=$boundary")
      flowFile.assertContentEquals(updatedContent(content, s"--$boundary", metadataString, metadata.name))
    }
  }

  def writeRawFileToGMData(rootUrl: Uri, headers: Headers, body: Array[Byte], boundary: String)(implicit client: Client[IO]) = {
    val request = Method.POST(body, rootUrl / "write")
    val multipartHeaders = Headers(List(
      `Transfer-Encoding`(TransferCoding.chunked),
      `Content-Type`(MediaType.multipartType("form-data", Some(boundary)))
    ))
    writeToGmData[List[Metadata]](client, multipartHeaders ++ headers, request, defaultHandleResponseFunction[List[Metadata]]).map(_.head)
  }

  describe("PrepareWriteRequest") {
    it("should append metadata to existing data in a flowfile and transfer the flowfile as successful") {
      runProcessorTests(attributeMap(_, _))(happyPathTest)()
    }

    it("should run successfully if a property is populated by the flowfile expression") {
      runProcessorTests(attributeMap(_, _, propertiesWithoutSecurity) ++ Map("gmdata.security" -> """{"label":"something","foreground":"something","background":"something"}"""), decode[Security](optionalProperties(securityProperty)).toOption)(happyPathTest)()
    }

    it("should transfer to a failure if a required attribute is missing from the flowfile") {
      runProcessorTests(halfAttributeMap(_, _)) { (runner, _, _) =>
        runner.assertTransferCount(RelSuccess, 0)
        runner.assertTransferCount(RelFailure, 1)
        for (flowFile: MockFlowFile <- runner.getFlowFilesForRelationship(RelFailure).asScala) {
          flowFile.assertAttributeExists("preparewriterequest.scala.exception.class")
          flowFile.assertAttributeExists("preparewriterequest.scala.exception.message")
        }
      }()
    }

    it("should throw an error if a required property is missing") {
      an[AssertionError] should be thrownBy runProcessorTests(attributeMap(_, _)) { (_, _, _) => } { (_, _) => }
    }
    it("should produce nothing but not crash if flowfile is null") {
      runProcessorTests(halfAttributeMap(_, _), enqueue = false) { (runner, _, _) =>
        runner.assertTransferCount(RelSuccess, 0)
        runner.assertTransferCount(RelFailure, 0)
      }()
    }
    it("should produce a flowfile that, injected into the body of a gm data request, will successfully write to gm data") {
      val sslContext = createSslContext(new File("../certs/nifinpe.jks"), "bmlmaXVzZXIK", new File("../certs/rootCA.jks"), "devotion")
      val rootUrl = Uri.fromString(gmDataUrl).right.get
      val headers = Headers(List(Header("USER_DN", "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")))
      (for {
        client <- BlazeClientBuilder[IO](ec, Some(sslContext)).withCheckEndpointAuthentication(false).allocated.map(_._1)
        parentOid <- getUserFolderOid(parse(objectPolicyOrganizationA).right.get)(rootUrl, headers, client)
        metadata = runProcessorTests(attributeMap(_, _), parentOid = parentOid) { (runner, expectedMetadata, _) =>
          for (flowFile: MockFlowFile <- runner.getFlowFilesForRelationship(RelSuccess).asScala) {
            val bytes = runner.getContentAsByteArray(flowFile)
            val boundary = new String(bytes).lines.toList(1).split("--")(1)
            writeRawFileToGMData(rootUrl, headers, bytes, boundary)(client).unsafeRunSync().parentoid shouldBe expectedMetadata.parentoid
          }
        }()
      } yield metadata).unsafeRunSync()
    }
  }
}
