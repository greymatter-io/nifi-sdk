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

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.deciphernow.greymatter.data.TestContext
import com.deciphernow.greymatter.data.nifi.http.Metadata
import com.deciphernow.greymatter.data.nifi.properties.ListFilesProperties
import io.circe.{Json, Printer}
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser.parse
import javax.net.ssl.SSLContext
import org.apache.nifi.util.{TestRunner, TestRunners}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Multipart, Part}
import org.http4s.{Header, Headers, MediaType, Method, Uri}
import org.scalatest._
import fs2.{Pure, Stream}
import org.apache.nifi.expression.ExpressionLanguageScope

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class ListFilesTest extends FunSpec with TestContext with Matchers with ListFilesProperties {
  implicit lazy val ec = ExecutionContext.global
  implicit val ctxShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)
  val headers = Headers(List(Header("USER_DN", "CN=nifinpe,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US")))
  val numberOfFiles = 3
  val numberOfFolders = 2
  val levels = 3
  val totalFileNumber = numberOfFiles * (scala.math.pow(numberOfFolders, levels + 1) - 1)
  override lazy val rootUrlProperty = rootUrlProp(List(), ExpressionLanguageScope.NONE)

  private def createMetadata(parentoid: String, objectPolicy: Json, action: String, mimeType: Option[String] = Some("text/plain"), name: String = randomString(), isFile: Option[Boolean] = Some(true)) = {
    Metadata(parentoid, name, objectPolicy, mimeType, None, action, None, None, None, None, None, None, None, None, isfile = isFile)
  }
  private def createFolderMetadata(name: String = randomString()) = createMetadata(_, _, _, None, name, None)

  private def createRandomFiles(objectPolicy: Json, action: String, directory: String, fileStream: Stream[Pure, Byte], name: String, folderName: String)(levels: Int, filesPerLevel: Int, foldersPerLevel: Int)(implicit sslContext: SSLContext, rootUrl: Uri, headers: Headers, client: Client[IO]) = for {
    userFolderOid <- getUserFolderOid(objectPolicy)
    newMetadata = List(createFolderMetadata(directory)(userFolderOid, objectPolicy, "U"))
    parentOid <- writeFolder(newMetadata, rootUrl, headers).map(_.oid.get)
    files <- createFiles(objectPolicy, action, parentOid, fileStream, name, folderName)("/", levels, filesPerLevel, foldersPerLevel)
  } yield files

  private def createFiles(objectPolicy: Json, action: String, parentOid: String, fileStream: Stream[Pure, Byte], name: String, folderName: String)(path: String, levels: Int, filesPerLevel: Int, foldersPerLevel: Int)(implicit rootUrl: Uri, headers: Headers, client: Client[IO]): IO[List[Metadata]] = for {
    files <- List.fill(filesPerLevel)(createMetadata(parentOid, objectPolicy, action, name = name)).traverse { metadata =>
      writeFile(List(metadata), rootUrl, headers, fileStream)
    }.map(_.map(_.copy(relativePath = Some(path))))
    folders <- List.fill(foldersPerLevel)(createFolderMetadata(folderName)(parentOid, objectPolicy, action)).traverse(metadata => writeFolder(List(metadata), rootUrl, headers))
    emptyMetadata = IO(List[Metadata]())
    moreFiles <- {
      if (levels > 0) folders.foldLeft(emptyMetadata) { (all, folder) =>
        all.flatMap { list =>
          val newPath = s"$path${folder.name}/"
          createFiles(objectPolicy, action, folder.oid.get, fileStream, name, folderName)(newPath, levels - 1, filesPerLevel, foldersPerLevel).map(_ ++ list)
        }
      }
      else emptyMetadata
    }
  } yield files ++ moreFiles

  private def writeFile(metadata: List[Metadata], rootUrl: Uri, headers: Headers, fileStream: Stream[Pure, Byte])(implicit client: Client[IO]) = {
    val printer = Printer.spaces2.copy(dropNullValues = true)
    val body = metadata.asJson.pretty(printer)
    val multipart = createMultipart(body, metadata.head.name, fileStream)
    val request = Method.POST(multipart, rootUrl / "write")
    writeToGmData[List[Metadata]](client, multipart.headers ++ headers, request).map(_.head)
  }

  private def createMultipart(metadata: String, fileName: String, fileStream: Stream[Pure, Byte]) = {
    val contentType = `Content-Type`(MediaType.application.json)
    Multipart[IO](Vector(Part.formData("meta", metadata), Part.fileData("blob", fileName, fileStream, contentType)))
  }

  private def runProcessorTests(setup: (TestRunner, String, Json) => List[Metadata], policies: List[String] = policies)(runnerTests: (TestRunner, List[Metadata], String) => Unit) = {
    policies.map(parse).map(_.right.get).foreach { objectPolicy =>
      val processor = new ListFiles
      val directory = randomString()
      val runner = TestRunners.newTestRunner(processor)
      val files = setup(runner, directory, objectPolicy)
      runner.run()
      iterateOrFail(runner)(_.getFlowFilesForRelationship(RelSuccess).asScala.length >= files.length)
      runnerTests(runner, files, directory)
    }
  }

  private def someDirectory(newDirectory: String) = s"/1/world/$npeEmail/$newDirectory"

  private def createFilesWithSSL(sslContext: SSLContext, objectPolicy: Json, action: String, directory: String, rootUrl: String, fileStream: Stream[Pure, Byte] = Stream.emits("".getBytes), name: String = randomString(), folderName: String = randomString()) = for {
    client <- BlazeClientBuilder[IO](ec, Some(sslContext)).withCheckEndpointAuthentication(false).allocated.map(_._1)
    url = Uri.fromString(rootUrl).right.get
    files <- createRandomFiles(objectPolicy, action, directory, fileStream, name, folderName)(levels, numberOfFiles, numberOfFolders)(sslContext, url, headers, client)
  } yield files.map(_.copy(rootUrlOption = Some(rootUrl)))

  private def iterateOrFail(runner: TestRunner, end: Int = 10,  start: Int = 0)(successCondition: TestRunner => Boolean): Unit = if(!successCondition(runner) && start < end) {
    runner.run()
    iterateOrFail(runner, end, start +1)(successCondition)
  }

  def happyPathTest(recurse: Boolean, action: String = "C", policies: List[String] = policies, rootUrl: String = gmDataUrl) = runProcessorTests({ (runner, directory, objectPolicy) =>
    val header: Header = headers.toList.head
    runner.setVariable(header.name.toString.toUpperCase, header.value)
    runner.setProperty(header.name.toString.toUpperCase, "${USER_DN}")
    runner.setProperty(rootUrlProperty, rootUrl)
    runner.setProperty(inputDirectoryProperty, someDirectory(directory))
    runner.setProperty(recurseProperty, recurse.toString)
    val sslContext = addSSLService(runner, sslContextServiceProperty)
    createFilesWithSSL(sslContext, objectPolicy, action, directory, rootUrl).unsafeRunSync()
  }, policies) _

  describe("ListFiles") {
    describe("when recurse is true") {
      it("should produce flowfiles for every file in gm data listed in the input directory, and recursively search folders until all files are found") {
        happyPathTest(true) { (runner, expectedFiles, _) =>
          runner.assertTransferCount(RelSuccess, totalFileNumber.toInt)
          runner.assertTransferCount(RelFailure, 0)
          val actualAttributes = runner.getFlowFilesForRelationship(RelSuccess).asScala.toList.map(_.getAttributes.asScala.toMap.filterNot(_._1 == "uuid"))
          actualAttributes.sortBy(_("file.lastModifiedTime")) shouldBe expectedFiles.map(_.attributeMap).sortBy(_("file.lastModifiedTime"))
        }
      }

      it("should be able to pick up new flowfiles without duplicating old ones") {
        happyPathTest(true) { (runner, expectedFiles, directory) =>
          val sslContext = getSSLContext(runner)
          val newFiles = createFilesWithSSL(sslContext, parse(policies.head).right.get, "C", directory, gmDataUrl).unsafeRunSync()
          val allFiles = newFiles ++ expectedFiles
          iterateOrFail(runner)(_.getFlowFilesForRelationship(RelSuccess).asScala.length >= allFiles.length)
          runner.assertTransferCount(RelSuccess, totalFileNumber.toInt * 2)
          runner.assertTransferCount(RelFailure, 0)
          val actualAttributes = runner.getFlowFilesForRelationship(RelSuccess).asScala.toList.map(_.getAttributes.asScala.toMap.filterNot(_._1 == "uuid"))
          actualAttributes.sortBy(_("file.lastModifiedTime")) shouldBe allFiles.map(_.attributeMap).sortBy(_("file.lastModifiedTime"))
        }
      }

      it("should reset the timestamp and pick up old files on any property change") {
        happyPathTest(true) { (runner, expectedFiles, directory) =>
          val sslContext = getSSLContext(runner)
          val newFiles = createFilesWithSSL(sslContext, parse(policies.head).right.get, "C", directory, gmDataUrl).unsafeRunSync()
          runner.setProperty(rootUrlProperty, "bad")
          runner.setProperty(rootUrlProperty, gmDataUrl)
          val allFiles = newFiles ++ expectedFiles ++ expectedFiles
          iterateOrFail(runner)(_.getFlowFilesForRelationship(RelSuccess).asScala.length >= allFiles.length)
          runner.assertTransferCount(RelSuccess, totalFileNumber.toInt * 3)
          runner.assertTransferCount(RelFailure, 0)
        }
      }

      it("should find the correct file owner and add it as an attribute") {
        happyPathTest(true, policies = List(objectPolicyOrganizationA)) { (runner, _, _) =>
          runner.assertTransferCount(RelSuccess, totalFileNumber.toInt)
          runner.assertTransferCount(RelFailure, 0)
          runner.getFlowFilesForRelationship(RelSuccess).asScala.toList.foreach(_.assertAttributeEquals("file.owner", "OrganizationA"))
        }
      }

      it("should only list files that match the file filter") {
        happyPathTest(true) { (runner, expectedFiles, directory) =>
          val sslContext = getSSLContext(runner)
          val name = randomString()
          def createFiles(name: String) = createFilesWithSSL(sslContext, parse(policies.head).right.get, "C", directory, gmDataUrl, name = name).unsafeRunSync()
          val newFiles = createFiles(name)
          createFiles(randomString())
          val allFiles = newFiles ++ expectedFiles
          runner.setProperty(fileFilterProperty, s"^$name$$")
          iterateOrFail(runner)(_.getFlowFilesForRelationship(RelSuccess).asScala.length >= allFiles.length)
          runner.assertTransferCount(RelSuccess, totalFileNumber.toInt * 2)
          runner.assertTransferCount(RelFailure, 0)
        }
      }

      it("should only list files that match the path filter") {
        happyPathTest(true) { (runner, expectedFiles, directory) =>
          val sslContext = getSSLContext(runner)
          def createFiles(name: String) = createFilesWithSSL(sslContext, parse(policies.head).right.get, "C", directory, gmDataUrl, folderName = name).unsafeRunSync()
          val folderName = randomString()
          runner.setProperty(pathFilterProperty, s"$folderName")
          createFiles(folderName)
          createFiles(randomString())
          val expectedNumberOfFiles = totalFileNumber.toInt * 2 + numberOfFiles * 2
          iterateOrFail(runner, 100)(_.getFlowFilesForRelationship(RelSuccess).asScala.length >= expectedNumberOfFiles)
          runner.assertTransferCount(RelSuccess, expectedNumberOfFiles)
          runner.assertTransferCount(RelFailure, 0)
        }
      }
      it("should only show files within the min and max file size property") {
        happyPathTest(true) { (runner, expectedFiles, directory) =>
          val sslContext = getSSLContext(runner)
          val goodFile = Stream.emits("AAAAAAAAAAAAAAA".getBytes)
          val tooBigFile = Stream.emits("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes)
          val tooSmallFile = Stream.emits("AA".getBytes)
          def createFiles(file: Stream[Pure, Byte]) = createFilesWithSSL(sslContext, parse(policies.head).right.get, "C", directory, gmDataUrl, fileStream = file)
          runner.setProperty(minFileSizeProperty, "10")
          runner.setProperty(maxFileSizeProperty, "20")
          (for{
            _ <- createFiles(goodFile)
            _ <-  createFiles(tooBigFile)
            create <-  createFiles(tooSmallFile)
          } yield create).unsafeRunSync()
          iterateOrFail(runner, 10)(_.getFlowFilesForRelationship(RelSuccess).asScala.length >= totalFileNumber.toInt * 2)
          runner.assertTransferCount(RelSuccess, totalFileNumber.toInt * 2)
          runner.assertTransferCount(RelFailure, 0)
        }
      }

      it("should only show files within the min and max file age property") {
        happyPathTest(true) { (runner, expectedFiles, directory) =>
          val sslContext = getSSLContext(runner)
          def createFiles = createFilesWithSSL(sslContext, parse(policies.head).right.get, "C", directory, gmDataUrl)
          def getSeconds = System.currentTimeMillis() / 1000
          val setup = for{
            _ <- createFiles
            _ <- IO.sleep(1 second)
            minAge = getSeconds
            files <- createFiles
            _ <- IO.sleep(1 second)
            maxAge = getSeconds
            _ <- createFiles
          } yield (files, minAge, maxAge)
          val (newFiles, minAge, maxAge) = setup.unsafeRunSync()
          runner.setProperty(minFileAgeProperty, s"$minAge")
          runner.setProperty(maxFileAgeProperty, s"$maxAge")
          iterateOrFail(runner, 10)(_.getFlowFilesForRelationship(RelSuccess).asScala.length >= (newFiles ++ expectedFiles).length)
          runner.assertTransferCount(RelSuccess, totalFileNumber.toInt * 2)
          runner.assertTransferCount(RelFailure, 0)
        }
      }
    }
    describe("when recurse is false") {
      it("should produce flowfiles for every file in gm data listed in the input directory") {
        happyPathTest(false) { (runner, _, _) =>
          runner.assertTransferCount(RelSuccess, numberOfFiles)
          runner.assertTransferCount(RelFailure, 0)
        }
      }
      it("should apply url filters correctly") {
        happyPathTest(false) { (runner, _, directory) =>
          val sslContext = getSSLContext(runner)
          def createFiles = createFilesWithSSL(sslContext, parse(policies.head).right.get, "C", directory, gmDataUrl).unsafeRunSync()
          val last = createFiles.map(_.tstamp.get).max
          createFiles
          runner.setProperty(urlFilterProperty, s"last=$last")
          iterateOrFail(runner)(_.getFlowFilesForRelationship(RelSuccess).asScala.length >= numberOfFiles * 2)
          runner.assertTransferCount(RelSuccess, numberOfFiles * 2)
          runner.assertTransferCount(RelFailure, 0)
        }
      }
    }
    it("should throw an error if a required property is missing") {
      an[AssertionError] should be thrownBy runProcessorTests { (_, _, _) => List() } { (_, _, _) => }
    }
  }
}
