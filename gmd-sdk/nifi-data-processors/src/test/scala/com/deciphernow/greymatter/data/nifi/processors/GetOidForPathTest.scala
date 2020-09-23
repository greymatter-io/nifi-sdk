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

import java.io.ByteArrayInputStream

import cats.effect.{ ContextShift, IO, Timer }
import com.deciphernow.greymatter.data.TestContext
import com.deciphernow.greymatter.data.nifi.http.Security
import com.deciphernow.greymatter.data.nifi.properties.GetOidForPathProperties
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import javax.net.ssl.SSLContext
import org.apache.nifi.util.{ MockFlowFile, TestRunner, TestRunners }
import org.http4s.{ Header, Headers, Uri }
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest._

import scala.concurrent.ExecutionContext

class GetOidForPathTest extends FunSpec with TestContext with Matchers with GetOidForPathProperties {

  case class Configuration(rootURL: String,
      fileName: String,
      objectPolicy: Json,
      originalObjectPolicy: Option[String],
      security: Option[Security],
      intermediate: Option[String],
      path: Option[String]) {
    lazy val rootURI = Uri.fromString(rootURL).right.get
  }

  import scala.collection.JavaConverters._

  val optionalProperties = commonOptionalProperties

  def configurations(implicit objectPolicy: Json) = {
    val security = decode[Security](optionalProperties(securityProperty)).toOption
    val origObjectPolicyProperty = Some(optionalProperties(originalObjectPolicyProperty))
    val simpleConfig = configuration(objectPolicy, None, None, None, None)
    val intermediate = "some/intermediate/folders/ between the / oth3r/folders"
    val path = "some folder name with spaces/someotherfolder"
    List(simpleConfig, simpleConfig.copy(intermediate = Some("."), path = Some(path)), simpleConfig.copy(intermediate = Some("/"), path = Some(path)), configuration(objectPolicy, security, origObjectPolicyProperty, Some(intermediate), Some(path)))
  }

  private val configuration = (objectPolicy: Json, security: Option[Security], originalObjectPolicy: Option[String], intermediate: Option[String], path: Option[String]) => {
    Configuration(gmDataUrl, "mycontext.txt", objectPolicy, originalObjectPolicy, security, intermediate, path)
  }

  def attributeMap(implicit configuration: Configuration) = Map("filename" -> configuration.fileName, "path" -> configuration.path.getOrElse("/"))

  private def runProcessorTests(getAttributes: Configuration => Map[String, String], enqueue: Boolean = true)(runnerTests: (TestRunner, Configuration, SSLContext) => Unit)(setProperties: (TestRunner, Configuration) => Unit = { (runner, configuration) =>
    runner.setProperty(rootUrlProperty, configuration.rootURL)
  })(implicit ctxShift: ContextShift[IO], ec: ExecutionContext) = {
    val someContent = "some content"
    val processor = new GetOidForPath
    policies.foreach { workingObjectPolicy =>
      implicit val policyJson = parse(workingObjectPolicy).right.get
      configurations.foreach { configuration: Configuration =>
        val runner = TestRunners.newTestRunner(processor)
        setProperties(runner, configuration)
        runner.setProperty(objectPolicyProperty, workingObjectPolicy)
        val sslContext = addSSLService(runner, sslContextServiceProperty)
        val content = new ByteArrayInputStream(someContent.getBytes)
        if(enqueue) runner.enqueue(content, getAttributes(configuration).asJava)
        runner.run()
        runnerTests(runner, configuration, sslContext)
      }
    }
  }

  implicit lazy val ec = ExecutionContext.global
  implicit val ctxShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  private def checkFolderOid(runner: TestRunner, sslContext: SSLContext, headers: Headers, configuration: Configuration) = {
    for (flowFile: MockFlowFile <- runner.getFlowFilesForRelationship(RelSuccess).asScala) {
      (for {
        client <- BlazeClientBuilder[IO](ec, Some(sslContext)).withCheckEndpointAuthentication(false).allocated.map(_._1)
        userFolderPath <- getUserFolderPath(configuration.rootURI, headers)(client)
        interm = configuration.intermediate.map("/" + _).getOrElse("")
        path = configuration.path.map("/" + _).getOrElse("")
        fullFolderPath = s"$userFolderPath$interm$path"
        folderOid <- checkForFolders(fullFolderPath, headers)(configuration.rootURI, client)
      } yield flowFile.assertAttributeEquals("gmdata.parentoid", folderOid)).unsafeRunSync()
    }
  }

  describe("GetOIDForPathProcessor") {
    it("should create folders in GM Data successfully given the correct properties") {
      runProcessorTests(attributeMap(_)) { (runner: TestRunner, configuration, sslContext) =>
        val headers = Headers(List(Header("USER_DN", "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")))
        runner.assertTransferCount(RelSuccess, 1)
        runner.assertTransferCount(RelFailure, 0)
        runner.assertAllFlowFilesContainAttribute("gmdata.parentoid")
        checkFolderOid(runner, sslContext, headers, configuration)
      } { (runner, config) =>
        runner.setProperty("USER_DN", "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")
        runner.setProperty(rootUrlProperty, config.rootURL)
        config.intermediate.map(runner.setProperty(intermediatePrefixProperty, _))
      }
    }

    it("should be able to read properties from environment variables") {
      runProcessorTests(attributeMap(_)) { (runner: TestRunner, configuration, sslContext) =>
        val headers = Headers(List(Header("USER_DN", "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")))
        runner.assertTransferCount(RelSuccess, 1)
        runner.assertTransferCount(RelFailure, 0)
        runner.assertAllFlowFilesContainAttribute("gmdata.parentoid")
        checkFolderOid(runner, sslContext, headers, configuration)
      } { (runner, config) =>
        runner.setVariable("USER_DN", "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")
        runner.setProperty("USER_DN", "${USER_DN}")
        runner.setProperty(rootUrlProperty, config.rootURL)//config.rootURL)
        config.intermediate.map(runner.setProperty(intermediatePrefixProperty, _))
      }
    }

    it("should create folders in GM Data successfully given the correct attributes") {
      runProcessorTests(attributeMap(_) ++ Map("USER_DN" -> "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")) { (runner: TestRunner, configuration, sslContext) =>
        runner.assertTransferCount(RelSuccess, 1)
        runner.assertTransferCount(RelFailure, 0)
        runner.assertAllFlowFilesContainAttribute("gmdata.parentoid")
        val headers = Headers(List(Header("USER_DN", "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")))
        checkFolderOid(runner, sslContext, headers, configuration)
      } { (runner, config) =>
        runner.setProperty(attributesToSendProperty, """\bUSER_DN\b""")
        runner.setProperty(rootUrlProperty, config.rootURL)
        config.intermediate.map(runner.setProperty(intermediatePrefixProperty, _))
      }
    }

    it("should throw an error if user dn is incorrect") {
      runProcessorTests(attributeMap(_) ++ Map("USER_DN" -> "CN=nifinpeBADNAME,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")) { (runner: TestRunner, configuration, sslContext) =>
        runner.assertTransferCount(RelSuccess, 0)
        runner.assertTransferCount(RelFailure, 1)
        for (flowFile: MockFlowFile <- runner.getFlowFilesForRelationship(RelFailure).asScala) {
          flowFile.assertAttributeExists("getoidforpath.scala.exception.class")
          flowFile.assertAttributeExists("getoidforpath.scala.exception.message")
        }
      } { (runner, config) =>
        runner.setProperty(attributesToSendProperty, """\bUSER_DN\b""")
        runner.setProperty(rootUrlProperty, config.rootURL)
        config.intermediate.map(runner.setProperty(intermediatePrefixProperty, _))
      }
    }

    it("Should fail to self-identify if attributes to send property is not set and gm data requires header") {
      runProcessorTests(attributeMap(_) ++ Map("USER_DN" -> "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")) { (runner: TestRunner, configuration, sslContext) =>
        runner.assertTransferCount(RelSuccess, 0)
        runner.assertTransferCount(RelFailure, 1)
        for (flowFile: MockFlowFile <- runner.getFlowFilesForRelationship(RelFailure).asScala) {
          flowFile.assertAttributeExists("getoidforpath.scala.exception.class")
          flowFile.assertAttributeExists("getoidforpath.scala.exception.message")
        }
      } ()
    }
    it("should produce nothing but not crash if flowfile is null") {
      runProcessorTests(attributeMap(_), enqueue = false) { (runner: TestRunner, _, _) =>
        runner.assertTransferCount(RelSuccess, 0)
        runner.assertTransferCount(RelFailure, 0)
      }()
    }
  }
}
