package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.sdkdetect.repository.SystemDetectorIntegrationTest.ScalaVersionParameter
import org.jetbrains.plugins.scala.project.utils.ScalaInstallationTestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.io.IOException
import java.nio.file.Files
import scala.collection.immutable.ListSet
import scala.jdk.CollectionConverters._

@RunWith(classOf[Parameterized])
class SystemDetectorIntegrationTest(parameter: ScalaVersionParameter) {

  @Test
  def systemDetectorIntegrationTest(): Unit = {
    testScalaVersion(parameter.scalaVersion)
  }

  private def testScalaVersion(scalaVersion: ScalaVersion): Unit = {
    val baseTempDir = Files.createTempDirectory("system-detector-test-sdk-root-")

    try {
      val scalaVersionStr = scalaVersion.minor

    val zipFile = ScalaInstallationTestUtils.downloadScalaDistribution(scalaVersion, baseTempDir)

    val tempDir = baseTempDir.resolve("scala-sdk")
    val unzippedDir = ScalaInstallationTestUtils.unzipScalaSdkArchive(zipFile, tempDir)

      val scalaSdkInnerDirNamePrefix = if (scalaVersion.isScala3) s"scala3-$scalaVersionStr" else s"scala-$scalaVersionStr"

      val filesInDir = unzippedDir.children()
      // Examples:
      // scala-2.13.16
      // scala3-3.3.6
      // scala3-3.7.1-RC2-aarch64-apple-darwin
      val scalaSdkRoot = filesInDir.find(f => f.isDirectory && f.getFileName.toString.startsWith(scalaSdkInnerDirNamePrefix)).getOrElse {
        fail(
          s"""Scala SDK dir not found for version $scalaVersionStr in $unzippedDir. Existing files:
             |${filesInDir.mkString("\n")}""".stripMargin).asInstanceOf[Nothing]
      }

      SystemDetector.buildSdkDescriptorFromFiles(Seq(scalaSdkRoot), indicator = new DumbProgressIndicator()) match {
        case Right(_) => //all good
        case Left(errors) =>
          val errorsConcatenated = s"""${errors.map(_.errorMessage).mkString("\n")}"""
          fail(
            s"""Cant build SDK descriptor for Scala SDK $scalaVersionStr at $scalaSdkRoot due to errors:
               |$errorsConcatenated""".stripMargin
          )
      }
    } finally {
      try NioFiles.deleteRecursively(baseTempDir)
      catch {
        case _: IOException if SystemInfo.isWindows =>
          // Ignore any exceptions occurring during temporary directory cleanup on Windows.
          // In the CI, this happens from time to time because the file is opened in some other process,
          // potentially Windows Defender.
          // The temporary directories will eventually be removed regardless.
      }
    }
  }
}

private object SystemDetectorIntegrationTest {
  // Useful for its toString method which prints the Scala version nicely for the test name.
  final case class ScalaVersionParameter(scalaVersion: ScalaVersion) {
    override def toString: String = scalaVersion.minor
  }

  @Parameterized.Parameters(name = "{0}")
  def parameters: java.util.Collection[ScalaVersionParameter] =
    scalaVersionsToTest.map(ScalaVersionParameter.apply).asJavaCollection

  private val IgnoredScalaVersions = Set(
    // too old versions, no one cares, need to delete it
    LatestScalaVersions.Scala_2_9,
    // we don't care about these as they were too early scala 3 SDKs
    LatestScalaVersions.Scala_3_0,
    LatestScalaVersions.Scala_3_1,
    LatestScalaVersions.Scala_3_2,
  )

  private def scalaVersionsToTest: Seq[ScalaVersion] = {
    val all = LatestScalaVersions.allStableWithoutScalaNext ++
      LatestScalaVersions.allScalaNext ++
      LatestScalaVersions.allReleaseCandidates

    (all.to(ListSet) -- IgnoredScalaVersions).toSeq.distinct
  }
}
