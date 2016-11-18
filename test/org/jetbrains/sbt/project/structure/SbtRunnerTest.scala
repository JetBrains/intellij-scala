package org.jetbrains.sbt
package project.structure

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

/**
 * @author Nikolay Obedin
 * @since 8/18/15.
 */
class SbtRunnerTest extends UsefulTestCase {

  def testSbtLaunch_0_12_4(): Unit =
    doTestSbtLauncherVersionDetection("0.12.4")

  def testSbtLaunch_0_13_0(): Unit =
    doTestSbtLauncherVersionDetection("0.13.0")

  def testSbtLaunch_0_13_1(): Unit =
    doTestSbtLauncherVersionDetection("0.13.1")

  def testSbtLaunch_0_13_2(): Unit =
    doTestSbtLauncherVersionDetection("0.13.2")

  def testSbtLaunch_0_13_5(): Unit =
    doTestSbtLauncherVersionDetection("0.13.5")

  def testSbtLaunch_0_13_6(): Unit =
    doTestSbtLauncherVersionDetection("0.13.6")

  def testSbtLaunch_0_13_7(): Unit =
    doTestSbtLauncherVersionDetection("0.13.7")

  def testSbtLaunch_0_13_8(): Unit =
    doTestSbtLauncherVersionDetection("0.13.8")

  def testSbtLaunch_0_13_9(): Unit =
    doTestSbtLauncherVersionDetection("0.13.9")

  def testSbtLaunch_0_13_11(): Unit =
    doTestSbtLauncherVersionDetection("0.13.11")

  //TODO
//  def testSbtLaunch_0_13_12(): Unit =
//    doTestSbtLauncherVersionDetection("0.13.12")

  def testSbtLaunch_0_13_13(): Unit =
    doTestSbtLauncherVersionDetection("0.13.13")

  def testMockLauncherWithoutSbtBootProperties(): Unit = {
    val expectedVersion = "1.0.0"
    val launcherFile = generateMockLauncher(expectedVersion)
    assertTrue(launcherFile.exists())
    val actualVersion = SbtRunner.detectSbtVersion(tmpDirFile, launcherFile)
    assertEquals(expectedVersion, actualVersion)
  }

  def testEmptyMockLauncher(): Unit = {
    val launcherFile = generateJarFileWithEntries()
    assertTrue(launcherFile.exists())
    val actualVersion = SbtRunner.detectSbtVersion(tmpDirFile, launcherFile)
    assertEquals(Sbt.LatestVersion, actualVersion)
  }

  private val tmpDirFile: File = new File(FileUtil.getTempDirectory)

  private def doTestSbtLauncherVersionDetection(sbtVersion: String): Unit = {
    val sbtLaunchJar = getSbtLaunchJarForVersion(sbtVersion)
    assertTrue(s"$sbtLaunchJar is not found. Make sure it is downloaded by Ivy.", sbtLaunchJar.exists())
    val actualVersion = SbtRunner.detectSbtVersion(tmpDirFile, sbtLaunchJar)
    assertEquals(sbtVersion, actualVersion)
  }

  private def getSbtLaunchJarForVersion(sbtVersion: String): File =
    new File(TestUtils.getIvyCachePath) / "org.scala-sbt" / "sbt-launch" / "jars" / s"sbt-launch-$sbtVersion.jar"

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
    using(new JarOutputStream(new BufferedOutputStream(new FileOutputStream(launcherFile)))) { out =>
      entries.foreach { case (name, contents) =>
        out.putNextEntry(new ZipEntry(name))
        out.write(contents.getBytes)
        out.closeEntry()
      }
    }
    launcherFile
  }
}
