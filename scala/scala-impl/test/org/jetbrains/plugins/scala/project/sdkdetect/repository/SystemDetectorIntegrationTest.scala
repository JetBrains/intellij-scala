package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.vfs.VirtualFileManager
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.apache.commons.io.FileUtils
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.project.utils.ScalaInstallationTestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith

import java.nio.file.Files
import scala.annotation.unused
import scala.collection.immutable.ListSet

@RunWith(classOf[JUnitParamsRunner])
class SystemDetectorIntegrationTest extends ScalaLightCodeInsightFixtureTestCase {

  @unused("used reflectively by the @Parameters annotation")
  private def scalaVersionsParameters: Array[AnyRef] =
    getScalaVersionsToTest.toArray.map { sv =>
      Array(sv.minor, sv)
    }

  @Test
  @Parameters(method = "scalaVersionsParameters")
  @TestCaseName("{method}[{0}]")
  def systemDetectorIntegrationTest(
    @unused("used reflectively by the @TestCaseName annotation") scalaVersionString: String,
    scalaVersion: ScalaVersion
  ): Unit = {
    testScalaVersion(scalaVersion)
  }

  private val IgnoredScalaVersions = Set(
    // too old versions, no one cares, need to delete it
    LatestScalaVersions.Scala_2_9,
    // we don't care about these as they were too early scala 3 SDKs
    LatestScalaVersions.Scala_3_0,
    LatestScalaVersions.Scala_3_1,
    LatestScalaVersions.Scala_3_2,
  )

  private def getScalaVersionsToTest: Seq[ScalaVersion] = {
    val all = LatestScalaVersions.allStableWithoutScalaNext ++
      LatestScalaVersions.allScalaNext ++
      LatestScalaVersions.allReleaseCandidates

    (all.to(ListSet) -- IgnoredScalaVersions).toSeq.distinct
  }

  private def testScalaVersion(scalaVersion: ScalaVersion): Unit = {
    val baseTempDir = Files.createTempDirectory("system-detector-test-sdk-root")
    baseTempDir.toFile.deleteOnExit()

    val scalaVersionStr = scalaVersion.minor

    val zipFile = ScalaInstallationTestUtils.downloadScalaDistribution(scalaVersion, baseTempDir)

    val tempDir = baseTempDir.resolve("scala-sdk")
    val unzippedDir = ScalaInstallationTestUtils.unzipScalaSdkArchive(zipFile, tempDir)

    val scalaSdkInnerDirNamePrefix = if (scalaVersion.isScala3) s"scala3-$scalaVersionStr" else s"scala-$scalaVersionStr"

    val filesInDir = unzippedDir.toFile.listFiles()
    // Examples:
    // scala-2.13.16
    // scala3-3.3.6
    // scala3-3.7.1-RC2-aarch64-apple-darwin
    val scalaSdkRoot = filesInDir.find(f => f.isDirectory && f.getName.startsWith(scalaSdkInnerDirNamePrefix)).getOrElse {
      fail(
        s"""Scala SDK dir not found for version $scalaVersionStr in $unzippedDir. Existing files:
           |${filesInDir.mkString("\n")}""".stripMargin).asInstanceOf[Nothing]
    }

    try {
      val scalaSdkRootVirtualFile = VirtualFileManager.getInstance().findFileByNioPath(scalaSdkRoot.toPath)
      SystemDetector.buildSdkDescriptor(Seq(scalaSdkRootVirtualFile)) match {
        case Right(_) => //all good
        case Left(errors) =>
          val errorsConcatenated = s"""${errors.map(_.errorMessage).mkString("\n")}"""
          fail(
            s"""Cant build SDK descriptor for Scala SDK $scalaVersionStr at $scalaSdkRoot due to errors:
               |$errorsConcatenated""".stripMargin
          )
      }
    } finally {
      // Clean up
      FileUtils.deleteDirectory(unzippedDir.toFile)
      Files.deleteIfExists(zipFile)
    }
  }
}
