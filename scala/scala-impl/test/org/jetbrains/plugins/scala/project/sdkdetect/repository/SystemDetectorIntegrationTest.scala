package org.jetbrains.plugins.scala.project.sdkdetect.repository

import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.system.CpuArch
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.sdkdetect.repository.SystemDetectorIntegrationTest.ScalaVersionParameter
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.io.IOException
import java.net.URI
import java.nio.file.{Files, Path}
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

      val downloadUrl = getScalaSdkArchiveDownloadUrl(scalaVersion)
      val zipFile = downloadScalaDistribution(downloadUrl, baseTempDir)

      val unzippedDir = unzipScalaSdkArchive(zipFile, baseTempDir)

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

  /**
   * Gets the download URL for a specific Scala version<br>
   * Examples:
   *  - For Scala 2.x: https://github.com/scala/scala/releases/download/v2.13.12/scala-2.13.12.zip
   *  - For Scala 3.3-3.4: https://github.com/scala/scala3/releases/download/3.3.6/scala3-3.3.6.zip
   *  - For Scala 3.5+: https://github.com/scala/scala3/releases/download/3.5.1/scala3-3.5.1-x86_64-apple-darwin.zip
   */
  private def getScalaSdkArchiveDownloadUrl(version: ScalaVersion): String = {
    val scalaVersion = version.minor

    if (version.isScala2) {
      s"https://github.com/scala/scala/releases/download/v$scalaVersion/scala-$scalaVersion.zip"
    }
    else if (version < ScalaVersion.Latest.Scala_3_5.withMinor(0)) {
      // Scala 3.0-3.4 has generic binaries
      s"https://github.com/scala/scala3/releases/download/$scalaVersion/scala3-$scalaVersion.zip"
    } else {
      // Scala 3.5+ has platform-specific binaries
      val platformPart = if (SystemInfo.isWindows)
        "x86_64-pc-win32"
      else if (SystemInfo.isMac)
        if (CpuArch.isArm64) "aarch64-apple-darwin" else "x86_64-apple-darwin"
      else if (CpuArch.isArm64) "aarch64-pc-linux" else "x86_64-pc-linux"

      s"https://github.com/scala/scala3/releases/download/$scalaVersion/scala3-$scalaVersion-$platformPart.zip"
    }
  }

  private def downloadScalaDistribution(urlString: String, baseTempDir: Path): Path = {
    val fileName = Path.of(new URI(urlString).toURL.getPath).getFileName.toString
    val targetFilePath = baseTempDir.resolve(fileName)

    if (Files.exists(targetFilePath) && Files.size(targetFilePath) > 0) {
      println(s"File already exists: $targetFilePath, skipping download")
      return targetFilePath
    }

    DownloadUtil.downloadFile(urlString, targetFilePath)
    targetFilePath
  }

  private def unzipScalaSdkArchive(zipFile: Path, baseTempDir: Path): Path = {
    val tempDir = baseTempDir.resolve("scala-sdk")
    Files.createDirectories(tempDir)
    println(s"Unzipping Scala distribution to ${tempDir.toAbsolutePath}")
    ZipUtils.unzip(zipFile, tempDir)
    tempDir
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
