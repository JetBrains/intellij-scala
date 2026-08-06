package org.jetbrains.sbt.project.versionNotifications

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import org.jetbrains.sbt.process.mock.MockSbtProcessForTestsSetup
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.{IdeaProjectFixtureOptions, TestProjectCopyOptions}
import org.jetbrains.sbt.project.versionNotifications.utils.ExternalSystemBuildMessageCollector
import org.jetbrains.sbt.{SbtTestDataUtils, SbtVersion}
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertTrue}

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Tests for [[LegacySbtVersionBuildToolWindowWarning]].
 *
 * High-level paths covered:
 *  - `warnForBuildToolWindowIfNeeded` under a mocked sbt import:
 *     - legacy ⇒ warning event on the external-system bus; the `MessageEvent`'s description carries the migration /
 *       "helpful resources" URLs and the attached navigatable is an `OpenFileDescriptor` for
 *       `project/build.properties`;
 *     - modern ⇒ no legacy warning event;
 *     - the same warning path under both `useSbtShellForImport = true/false` modes (concrete subclasses).
 *
 * The project-notification counterpart lives in [[LegacySbtVersionProjectNotificationTest]].
 *
 * @note Extends [[SbtExternalSystemImportingTestLike]] for the sbt-bound external-system import pipeline:
 *       a linked [[org.jetbrains.sbt.project.settings.SbtProjectSettings]] under
 *       [[org.jetbrains.sbt.project.SbtProjectSystem.Id]] plus
 *       `importProject` firing events on [[ExternalSystemProgressNotificationManager]].
 *
 *       The class is abstract so concrete subclasses can exercise the same warning path under both
 *       `useSbtShellForImport` modes.
 */
abstract class LegacySbtVersionBuildToolWindowWarningTestBase extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String = {
    // NOTE: this could be any sbt project.
    // The actual sbt version is explicitly set in each test anyway (it overwrites the copied files).
    SbtTestDataUtils.resolveRelativePath("sbt-shell-runtime-tests/testdata/sbt/shell/sbtTestRunTest_0_13")
  }

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def getIdeaProjectFixtureOptions: IdeaProjectFixtureOptions =
    super.getIdeaProjectFixtureOptions.copy(useTestProjectAsIdeaProjectRoot = true)

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()
    // Whether the import uses the sbt shell is driven by the production setting `useSbtShellForImport`; legacy sbt
    // cannot use the new shell anyway. Here we only need to substitute the mock process.
    MockSbtProcessForTestsSetup.enableMockSbtProcess(getMyProject, getTestRootDisposable)
  }

  def test_LegacySbtVersion_MockSbtImport_ShouldReportWarningInBuildToolWindow(): Unit = {
    writeBuildProperties("sbt.version=0.13.18")
    val warningEvents = importProjectAndCollectBuildWarningEvents()

    val expectedMessage = expectedLegacyBuildToolWindowWarning(SbtVersion("0.13.18"))
    val legacyWarning = warningEvents.find(_.getMessage.contains(expectedMessage)).getOrElse(
      throw new AssertionError(
        s"Expected legacy sbt warning '$expectedMessage' in Build Tool Window warnings, got: ${warningEvents.map(_.getMessage).mkString("[", ", ", "]")}"
      )
    )

    val description = legacyWarning.getDescription
    assertNotNull("Expected description on legacy sbt warning", description)
    // The "helpful resources" block interpolates these three URLs (see LegacySbtVersionBuildToolWindowWarning).
    Seq(
      "Migrating-from-sbt-013x",
      "github.com/sbt/sbt/releases",
      "scala-sbt.org/download",
    ).foreach { urlFragment =>
      assertTrue(
        s"Expected description to mention '$urlFragment', got:\n$description",
        description.contains(urlFragment),
      )
    }

    val navigatable = legacyWarning.getNavigatable(getMyProject)
    assertNotNull("Expected an OpenFileDescriptor navigatable on legacy sbt warning", navigatable)
    navigatable match {
      case openFileDescriptor: OpenFileDescriptor =>
        assertEquals(
          buildPropertiesPath.toAbsolutePath.normalize(),
          openFileDescriptor.getFile.toNioPath.toAbsolutePath.normalize(),
        )
      case other =>
        throw new AssertionError(s"Expected navigatable to be an OpenFileDescriptor, got ${other.getClass.getName}")
    }
  }

  def test_ModernSbtVersion_MockSbtImport_ShouldNotReportWarningInBuildToolWindow(): Unit = {
    writeBuildProperties("sbt.version=1.10.0")
    val warningMessages = importProjectAndCollectBuildWarningEvents().map(_.getMessage)

    assertFalse(
      s"Did not expect legacy sbt warning in Build Tool Window warnings, got: ${warningMessages.mkString("[", ", ", "]")}",
      warningMessages.exists(isLegacyBuildToolWindowWarning),
    )
  }

  // `lazy val`, not `val`: `getTestProjectPath` is itself a `lazy val` backed by `testProjectRootFixture`, which is
  // populated by the JUnit fixture setUp rather than the constructor.
  private lazy val buildPropertiesPath =
    getTestProjectPath.resolve("project").resolve("build.properties")

  private def writeBuildProperties(text: String): Unit =
    Files.writeString(buildPropertiesPath, text, StandardCharsets.UTF_8)

  // Keeps the BTW tests focused on notification reporting; the real sbt process is replaced by a mock in `setupBeforeProjectImport`.
  private def importProjectAndCollectBuildWarningEvents(): Seq[MessageEvent] = {
    val listener = new ExternalSystemBuildMessageCollector
    val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
    notificationManager.addNotificationListener(listener)
    try {
      importProject()
    } finally {
      notificationManager.removeNotificationListener(listener)
    }

    listener.getWarningEvents
  }

  private def expectedLegacyBuildToolWindowWarning(sbtVersion: SbtVersion): String =
    s"legacy sbt version detected ($sbtVersion)"

  // Version-independent prefix, so the negative assertion does not need to know which sbt version a stray warning would carry.
  private val LegacyBuildToolWindowWarningPrefix: String = "legacy sbt version detected"

  private def isLegacyBuildToolWindowWarning(message: String): Boolean =
    message.contains(LegacyBuildToolWindowWarningPrefix)
}

class LegacySbtVersionBuildToolWindowWarningTest_WithSbtShell extends LegacySbtVersionBuildToolWindowWarningTestBase {

  override protected def getTestSbtProjectSettings =
    super.getTestSbtProjectSettings.copy(useSbtShellForImport = true)
}

class LegacySbtVersionBuildToolWindowWarningTest_WithoutSbtShell extends LegacySbtVersionBuildToolWindowWarningTestBase {

  override protected def getTestSbtProjectSettings =
    super.getTestSbtProjectSettings.copy(useSbtShellForImport = false)
}
