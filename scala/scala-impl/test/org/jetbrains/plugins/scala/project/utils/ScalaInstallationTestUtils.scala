package org.jetbrains.plugins.scala.project.utils

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.system.CpuArch
import org.jetbrains.plugins.scala.ScalaVersion

import java.net.URI
import java.nio.file.{Files, Path}

/**
 * Utility methods for downloading and installing Scala distributions in tests.
 */
object ScalaInstallationTestUtils {

  /**
   * Downloads Scala distribution to the specified directory.
   */
  def downloadScalaDistribution(scalaVersion: ScalaVersion, baseTempDir: Path): Path = {
    val downloadUrl = getScalaSdkArchiveDownloadUrl(scalaVersion)
    val fileName = Path.of(new URI(downloadUrl).toURL.getPath).getFileName.toString
    val targetFilePath = baseTempDir.resolve(fileName)

    if (Files.exists(targetFilePath) && Files.size(targetFilePath) > 0) {
      println(s"File already exists: $targetFilePath, skipping download")
      return targetFilePath
    }

    DownloadUtil.downloadFile(downloadUrl, targetFilePath)
    targetFilePath
  }

  /**
   * Gets the download URL for a specific Scala version. <br>
   *
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

  /**
   * Unzips Scala SDK archive to the specified directory.
   *
   * @param zipFile the zip file to extract
   * @param tempDir the directory to extract to
   * @param customRootDir optional custom name for the root directory (replaces the first path component in the archive)
   */
  def unzipScalaSdkArchive(zipFile: Path, tempDir: Path, customRootDir: Option[String] = None): Path = {
    Files.createDirectories(tempDir)
    println(s"Unzipping Scala distribution to ${tempDir.toAbsolutePath}")
    ZipUtils.unzip(zipFile, tempDir, customRootDir)
    tempDir
  }
}
