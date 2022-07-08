package org.jetbrains.sbt
package project.structure

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.TestDependencyManager
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtUtil._
import org.junit.Assert._

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import scala.util.Using

class SbtRunnerTest extends UsefulTestCase {

  def testSbtLaunch_0_12_4(): Unit =
    doTestSbtLauncherVersionDetection("0.12.4")

  def testSbtLaunch_0_13_0(): Unit =
    doTestSbtLauncherVersionDetection("0.13.0")

  def testSbtLaunch_0_13_5(): Unit =
    doTestSbtLauncherVersionDetection("0.13.5")

  def testSbtLaunch_0_13_9(): Unit =
    doTestSbtLauncherVersionDetection("0.13.9")

  def testSbtLaunch_latest_0_13(): Unit =
    doTestSbtLauncherVersionDetection(BuildInfo.sbtLatest_0_13)

  def testSbtLaunch_latest(): Unit =
    doTestSbtLauncherVersionDetection(BuildInfo.sbtLatestVersion)


  def testMockLauncherWithoutSbtBootProperties(): Unit = {
    val expectedVersion = "1.0.0"
    val launcherFile = generateMockLauncher(expectedVersion)
    assertTrue(launcherFile.exists())
    val actualVersion = detectSbtVersion(tmpDirFile, launcherFile)
    assertEquals(expectedVersion, actualVersion)
  }

  def testEmptyMockLauncher(): Unit = {
    val launcherFile = generateJarFileWithEntries()
    assertTrue(launcherFile.exists())
    val actualVersion = detectSbtVersion(tmpDirFile, launcherFile)
    assertEquals(BuildInfo.sbtLatestVersion, actualVersion)
  }

  private val tmpDirFile: File = new File(FileUtil.getTempDirectory)

  private def doTestSbtLauncherVersionDetection(sbtVersion: String): Unit = {
    val sbtLaunchJar = TestDependencyManager.forSbtVersion(Version(sbtVersion)).resolveSingle("org.scala-sbt" % "sbt-launch" % sbtVersion).file
    assertTrue(s"$sbtLaunchJar is not found. Make sure it is downloaded by Ivy.", sbtLaunchJar.exists())
    val actualVersion = detectSbtVersion(tmpDirFile, sbtLaunchJar)
    assertEquals(sbtVersion, actualVersion)
  }

  private def generateMockLauncher(implementationVersion: String): File = {
    val manifestContents =
      s"""|Manifest-Version: 1.0
          |Implementation-Vendor: com.example
          |Implementation-Title: launcher
          |Implementation-Version: $implementationVersion
          |Main-Class: com.example.Main
      """.stripMargin
    generateJarFileWithEntries("META-INF/MANIFEST.MF" -> manifestContents)
  }

  private def generateJarFileWithEntries(entries: (String, String)*): File = {
    val launcherFile = FileUtil.createTempFile("mockLauncher", ".jar", true)
    Using.resource(new JarOutputStream(new BufferedOutputStream(new FileOutputStream(launcherFile)))) { out =>
      entries.foreach { case (name, contents) =>
        out.putNextEntry(new ZipEntry(name))
        out.write(contents.getBytes)
        out.closeEntry()
      }
    }
    launcherFile
  }
}
