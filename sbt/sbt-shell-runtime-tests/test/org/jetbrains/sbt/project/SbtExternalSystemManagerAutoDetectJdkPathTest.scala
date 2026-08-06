package org.jetbrains.sbt.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.{HeavyPlatformTestCase, IdeaTestUtil}
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction}
import org.jetbrains.sbt.SbtVersion
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail}

import java.nio.file.Path
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * Tests the branching logic in [[SbtExternalSystemManager.getAutoDetectJdkPath]] with the real auto-detect provider.
 *
 * The recording provider lets the tests verify not only the selected JDK path, but also how that result was obtained,
 * while delegating candidate detection and preconfiguration to production code.
 *
 * The find-call count distinguishes the fast path from the "preconfigure and retry" path.
 *
 * The preconfigure-call count verifies that preconfiguration is skipped from a read action and performed otherwise.
 *
 * The EDT flag checks that preconfiguration still happens on the dispatch thread via `invokeAndWait`.
 */
class SbtExternalSystemManagerAutoDetectJdkPathTest extends HeavyPlatformTestCase {

  override def runInDispatchThread(): Boolean = false

  def testSuitableJdkInNonEmptyJdkTable_ShouldReturnJdkPathWithoutPreconfiguration(): Unit = {
    disableJavaDetector()
    val jdk = testJdk
    val provider = recordingProvider()

    val detectedPath = withJdksInTable(jdk) {
      autoDetectJdkPath(provider, expectedJdkTableIsEmpty = false)
    }

    assertEquals(Some(javaExecutablePath(jdk)), detectedPath)
    assertEquals(1, provider.findCallCount.get())
    assertEquals(0, provider.preconfigureCallCount.get())
  }

  def testUnsupportedJdkInNonEmptyJdkTable_ShouldReturnFallbackJdkPathAfterPreconfiguration(): Unit = {
    disableJavaDetector()
    val jdk = unsupportedTestJdk
    val provider = recordingProvider()

    val detectedPath = withJdksInTable(jdk) {
      autoDetectJdkPath(provider, expectedJdkTableIsEmpty = false)
    }

    assertEquals(Some(javaExecutablePath(jdk)), detectedPath)
    assertEquals(2, provider.findCallCount.get())
    assertEquals(1, provider.preconfigureCallCount.get())
    assertTrue(provider.preconfigureWasCalledFromDispatchThread.get())
  }

  def testUnsupportedJdkInNonEmptyJdkTable_ReadAction_ShouldReturnFallbackJdkPathWithoutPreconfiguration(): Unit = {
    val jdk = unsupportedTestJdk
    val provider = recordingProvider()

    val detectedPath = withJdksInTable(jdk) {
      inReadAction {
        autoDetectJdkPath(provider, expectedJdkTableIsEmpty = false)
      }
    }

    assertEquals(Some(javaExecutablePath(jdk)), detectedPath)
    assertEquals(2, provider.findCallCount.get())
    assertEquals(0, provider.preconfigureCallCount.get())
    assertFalse(provider.preconfigureWasCalledFromDispatchThread.get())
  }

  def testNoSuitableJdkAndNoFallbackJdk_EmptyJdkTable_NoReadAction_ShouldReturnNoneAfterPreconfiguration(): Unit = {
    disableJavaDetector()
    val jdkTable = ProjectJdkTable.getInstance(getProject)
    val provider = recordingProvider()

    val detectedPath = autoDetectJdkPath(provider)

    assertEquals(None, detectedPath)
    assertEquals(2, provider.findCallCount.get())
    assertEquals(1, provider.preconfigureCallCount.get())
    assertTrue(provider.preconfigureWasCalledFromDispatchThread.get())
    assertJdkTableIsEmpty(jdkTable)
  }

  def testNoSuitableJdkAndNoFallbackJdk_EmptyJdkTable_ReadAction_ShouldReturnNoneWithoutPreconfiguration(): Unit = {
    val provider = recordingProvider()

    val detectedPath = inReadAction {
      autoDetectJdkPath(provider)
    }

    assertEquals(None, detectedPath)
    assertEquals(2, provider.findCallCount.get())
    assertEquals(0, provider.preconfigureCallCount.get())
    assertFalse(provider.preconfigureWasCalledFromDispatchThread.get())
  }

  private def autoDetectJdkPath(provider: SbtExternalSystemManager.AutoDetectJdkProvider): Option[Path] = {
    val jdkTable = ProjectJdkTable.getInstance(getProject)
    autoDetectJdkPath(provider, jdkTable, expectedJdkTableIsEmpty = true)
  }

  private def autoDetectJdkPath(
    provider: SbtExternalSystemManager.AutoDetectJdkProvider,
    expectedJdkTableIsEmpty: Boolean,
  ): Option[Path] = {
    val jdkTable = ProjectJdkTable.getInstance(getProject)
    autoDetectJdkPath(provider, jdkTable, expectedJdkTableIsEmpty)
  }

  private def autoDetectJdkPath(
    provider: SbtExternalSystemManager.AutoDetectJdkProvider,
    jdkTable: ProjectJdkTable,
    expectedJdkTableIsEmpty: Boolean,
  ): Option[Path] = {
    if (expectedJdkTableIsEmpty) {
      assertJdkTableIsEmpty(jdkTable)
    }

    SbtExternalSystemManager.getAutoDetectJdkPath(
      getProject,
      SbtVersion("1.10.0"),
      jdkTable,
      provider,
    )
  }

  private def assertJdkTableIsEmpty(jdkTable: ProjectJdkTable): Unit = {
    val availableJdks = jdkTable.getSdksOfType(JavaSdk.getInstance()).asScala.toSeq
    if (availableJdks.nonEmpty) {
      fail(
        "Project-level JDK table should be empty before the test setup. " +
          s"Available JDKs: ${availableJdks.map(_.toString).mkString(", ")}"
      )
    }
  }

  private def withJdksInTable[T](jdks: Sdk*)(body: => T): T = {
    val jdkTable = ProjectJdkTable.getInstance(getProject)
    assertJdkTableIsEmpty(jdkTable)

    inWriteAction {
      jdks.foreach(jdkTable.addJdk)
    }
    try body
    finally {
      inWriteAction {
        jdks.foreach(jdkTable.removeJdk)
      }
    }
  }

  private def testJdk: Sdk =
    IdeaTestUtil.getMockJdk(JavaVersion.compose(17))

  private def unsupportedTestJdk: Sdk =
    IdeaTestUtil.getMockJdk(JavaVersion.compose(26))

  private def javaExecutablePath(jdk: Sdk): Path =
    Path.of(JavaSdk.getInstance().getVMExecutablePath(jdk))

  private def disableJavaDetector(): Unit =
    Registry.get("java.detector.enabled").setValue(false, getTestRootDisposable)

  private def recordingProvider(): RecordingAutoDetectJdkProvider =
    new RecordingAutoDetectJdkProvider(SbtExternalSystemManager.SbtProcessAutoDetectJdkProvider)

  private final class RecordingAutoDetectJdkProvider(
    delegate: SbtExternalSystemManager.AutoDetectJdkProvider
  ) extends SbtExternalSystemManager.AutoDetectJdkProvider {

    val findCallCount = new AtomicInteger()
    val preconfigureCallCount = new AtomicInteger()
    val preconfigureWasCalledFromDispatchThread = new AtomicBoolean(false)

    override def findJdkWithSuitableVersion(
      jdkTable: ProjectJdkTable,
      sbtVersion: SbtVersion,
    ): SbtProcessJdkGuesser.SdkCandidate = {
      findCallCount.incrementAndGet()

      delegate.findJdkWithSuitableVersion(jdkTable, sbtVersion)
    }

    override def preconfigureJdkForSbt(project: Project, jdkTable: ProjectJdkTable, sbtVersion: SbtVersion): Unit = {
      preconfigureCallCount.incrementAndGet()
      preconfigureWasCalledFromDispatchThread.set(ApplicationManager.getApplication.isDispatchThread)

      delegate.preconfigureJdkForSbt(project, jdkTable, sbtVersion)
    }
  }
}
