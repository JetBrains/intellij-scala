package org.jetbrains.sbt.project.structure

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.{TestApplicationManager, UsefulTestCase}
import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.dependencymanager.TestDependencyManagerForSbt
import org.jetbrains.sbt.{SbtVersion, SbtVersionDetector}
import org.junit.Assert._

import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.file.{Files, Path}
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import scala.util.Using

class SbtVersionDetectorTest extends UsefulTestCase {

  override def setUp(): Unit = {
    super.setUp()

    // NOTE: need to initialize it manually to avoid exception thrown from
    // com.intellij.openapi.progress.ProgressManager.getInstance
    // It calls `ApplicationManager.getApplication` which can be not initialized in tests
    // If you run this test exclusively
    TestApplicationManager.getInstance
  }

  def testSbtLaunch_latest_0_13(): Unit =
    doTestSbtLauncherVersionDetection(SbtVersion.Latest.Sbt_0_13)

  def testSbtLaunch_latest_1(): Unit =
    doTestSbtLauncherVersionDetection(SbtVersion.Latest.Sbt_1)

  def testSbtLaunch_latest_2(): Unit =
    doTestSbtLauncherVersionDetection(SbtVersion.Latest.Sbt_2)

  def testEmptyMockLauncher(): Unit = {
    val launcherFile = generateJarFileWithEntries()
    assertTrue(launcherFile.exists)
    val actualVersion = SbtVersionDetector.detectSbtVersion(tmpDirFile, launcherFile)
    assertEquals(SbtVersion.Latest.Sbt_1, actualVersion)
  }

  private val tmpDirFile: Path = Path.of(FileUtil.getTempDirectory)

  private def doTestSbtLauncherVersionDetection(sbtVersion: SbtVersion): Unit = {
    val sbtLaunchJar = new TestDependencyManagerForSbt(sbtVersion).resolveSingle("org.scala-sbt" % "sbt-launch" % sbtVersion.minor).file
    assertTrue(s"$sbtLaunchJar is not found. Make sure it is downloaded by Ivy.", Files.exists(sbtLaunchJar))

    val actualVersion = SbtVersionDetector.detectSbtVersion(tmpDirFile, sbtLaunchJar)
    assertEquals(sbtVersion, actualVersion)
  }

  private def generateJarFileWithEntries(entries: (String, String)*): Path = {
    val launcherFile = FileUtil.createTempFile("mockLauncher", ".jar", true)
    Using.resource(new JarOutputStream(new BufferedOutputStream(new FileOutputStream(launcherFile)))) { out =>
      entries.foreach { case (name, contents) =>
        out.putNextEntry(new ZipEntry(name))
        out.write(contents.getBytes)
        out.closeEntry()
      }
    }
    launcherFile.toPath
  }
}
