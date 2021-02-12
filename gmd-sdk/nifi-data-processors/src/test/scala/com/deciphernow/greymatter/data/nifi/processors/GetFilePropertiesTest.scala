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

import cats.effect.{ContextShift, IO, Timer}
import com.deciphernow.greymatter.data.TestContext
import com.deciphernow.greymatter.data.nifi.http.Metadata
import com.deciphernow.greymatter.data.nifi.properties.GetFilePropertiesProperties
import io.circe.generic.auto._
import io.circe.parser._
import org.apache.nifi.util.{TestRunner, TestRunners}
import org.http4s.{Header, Headers}
import org.http4s.dsl.Http4sDsl
import org.scalatest._

import scala.concurrent.ExecutionContext

class GetFilePropertiesTest extends FunSpec with TestContext with Matchers with GetFilePropertiesProperties with Http4sDsl[IO]{

  import scala.collection.JavaConverters._

  private def runProcessorTests(setup: (TestRunner, String) => List[Metadata])(runnerTests: (TestRunner, List[Metadata], String) => Unit) = {
    val processor = new GetFileProperties
    val directory = randomString()
    val runner = TestRunners.newTestRunner(processor)
    val files = setup(runner, directory)
    runner.run()
    runnerTests(runner, files, directory)
  }
  override lazy val rootUrlProperty = rootUrlProp()


  implicit lazy val ec = ExecutionContext.global
  implicit val ctxShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  val headers = Headers(List(Header("USER_DN", "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")))


  def commonSetup(runner: TestRunner) = {
    val header: Header = headers.toList.head
    runner.setVariable(header.name.toString.toUpperCase, header.value)
    runner.setVariable("ROOT_URL", gmDataUrl)
    runner.setProperty(header.name.toString.toUpperCase, "${USER_DN}")
    runner.setProperty(rootUrlProperty, "${ROOT_URL}")
    addSSLService(runner, sslContextServiceProperty)
  }

  def happyPathTest(recurse: Boolean, action: String = "C", rootUrl: String = gmDataUrl) = runProcessorTests({ (runner, directory) =>
    val sslContext = commonSetup(runner)
    val files = createFilesWithSSL(sslContext, parse(policies.head).right.get, action, directory, rootUrl, 0, 1, 1, headers).unsafeRunSync()
    val content = new ByteArrayInputStream("".getBytes)
    files.map(metadata => Map("filename" -> metadata.name, "path" -> s"/$directory").asJava).foreach {
      runner.enqueue(content, _)
    }
    files
  }) _

  describe("GetFileProperties processor") {
    it("should get properties for a file if it exists as well as a successful status code") {
      happyPathTest(true) { (runner, expectedFiles, _) =>
        runner.assertTransferCount(RelSuccess, 1)
        runner.assertTransferCount(RelFailure, 0)
        val actualAttributes = runner.getFlowFilesForRelationship(RelSuccess).asScala.toList.map(_.getAttributes.asScala.toMap).head
        actualAttributes("gmdata.status.code") shouldBe "200"
        decode[Metadata](actualAttributes("gmdata.file.props")).isRight shouldBe true
      }
    }
    it("should get an error response code and an error message if a file doesn't exist in gm data") {
      runProcessorTests{ (runner, _) =>
        commonSetup(runner)
        val content = new ByteArrayInputStream("".getBytes)
        runner.enqueue(content, Map("filename" -> "notrealfilename", "path" -> s"/some/bad/path").asJava)
        List()
      } { (runner, _, _) =>
        runner.assertTransferCount(RelSuccess, 1)
        runner.assertTransferCount(RelFailure, 0)
        val actualAttributes = runner.getFlowFilesForRelationship(RelSuccess).asScala.toList.map(_.getAttributes.asScala.toMap).head
        actualAttributes("gmdata.status.code") shouldBe "404"
        decode[Metadata](actualAttributes("gmdata.file.props")).isLeft shouldBe true
      }
    }
  }
}
