package org.jetbrains.bsp.protocol

import com.intellij.notification.{Notification, NotificationType}
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.bsp.settings.BspProjectSettings.{AutoConfig, BspConfigFile, BspServerConfig}
import org.jetbrains.bsp.{BSP, BspBundle, BspProjectStructureImportingTestUtils, BspUtil, SbtOverBspExternalSystemImportingTestCase}
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.notifications.CollectingNotificationsListener
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.ProjectStructureDsl.*
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext
import org.jetbrains.sbt.project.{ExactMatch, ProjectStructureAssertionsFixture, ProjectStructureMatcher}
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue, fail}
import org.junit.experimental.categories.Category

import java.nio.file.Files
import scala.util.Random

/**
 * Tests BSP connection file regeneration when the file is missing or broken at import time.
 * Additionally, verifies that the "config changed" notification is displayed (or not).
 *
 * @see [[BspConnectionFileNotificationService.showConfigChangedNotification]]
 */
@Category(Array(classOf[SlowTests2]))
class BspConfigRegenerationIntegrationTest extends SbtOverBspExternalSystemImportingTestCase
  with ProjectStructureMatcher
  with ExactMatch {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/simple"

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  // All tests in this class cover cases where, at import time, either no BSP
  // connection file exists or the existing one is broken. In such cases, the
  // connection file should not be regenerated in advance.
  override protected def generateSbtBspConfigurationFileIfNeeded(): Unit = {}

  protected lazy val projectStructureAssertions: ProjectStructureAssertionsFixture =
    new ProjectStructureAssertionsFixture(getMyProject)

  def test_whenConnectionFileMissing_shouldRegenerateWithAutoConfig(): Unit =
    runImportAndVerify()

  def test_whenConnectionFileMissing_andServerConfigPointsToNonexistentPath_shouldResetToAutoConfig(): Unit = {
    adjustProjectSettings { settings =>
      settings.serverConfig = BspConfigFile(getTestProjectPath / "temp")
    }

    runImportAndVerify(serverConfig = AutoConfig)
  }

  def test_whenConnectionFileMissing_andServerConfigPointsToExistingBspFile_shouldPreserveServerConfig(): Unit = {
    adjustProjectSettings { settings =>
      settings.serverConfig = BspConfigFile(getTestProjectPath.resolve(".bsp/sbt.json"))
    }

    runImportAndVerify(
      serverConfig = BspConfigFile(getTestProjectPath.resolve(".bsp/sbt.json")),
    )
  }

  // Verifies that regeneration before BSP server startup is not triggered
  // (it's confirmed by the assertion on the "config changed" notification content),
  // even though `BspProjectSettings.autoRegenerateBspConfigOnServerStartup` is enabled,
  // because this setting applies only to Scala CLI and Mill projects.
  def test_whenBrokenFile_andAutoRegenerateEnabled_shouldShowNotification(): Unit = {
    createBrokenConnectionFile(fileName = "sbt.json")

    adjustProjectSettings { settings =>
      settings.autoRegenerateBspConfigOnServerStartup = true
    }

    runImportAndVerify(
      configChangedNotification = true,
      autoRegenerate = true
    )
  }

  def test_whenBrokenFile_andConfigNotMarkedAsGenerated_shouldShowNotification(): Unit = {
    createBrokenConnectionFile(fileName = "sbt.json")

    adjustProjectSettings { settings =>
      settings.connectionFileHash = currentConnectionFileHash
    }

    runImportAndVerify(configChangedNotification = true)
  }

  def test_whenBrokenFile_andConnectionFileChangedExternally_shouldShowNotification(): Unit = {
    createBrokenConnectionFile(fileName = "sbt.json")

    adjustProjectSettings { settings =>
      settings.bspConfigGenerated = true
      settings.connectionFileHash = connectionFileHashDifferentFromCurrent
    }

    runImportAndVerify(
      configChangedNotification = true,
      bspConfigGenerated = true
    )
  }

  def test_whenBrokenFile_andConnectionFileNotChangedExternally_shouldNotShowNotification(): Unit = {
    createBrokenConnectionFile(fileName = "sbt.json")

    adjustProjectSettings { settings =>
      settings.bspConfigGenerated = true
      settings.connectionFileHash = currentConnectionFileHash
    }

    runImportAndVerify(
      bspConfigGenerated = true
    )
  }

  def test_whenMultipleBrokenFiles_andConfigNotMarkedAsGenerated_shouldShowNotificationAndAdjustServerConfig(): Unit = {
    createBrokenConnectionFile(fileName = "other.json")
    createBrokenConnectionFile(fileName = "sbt.json")

    adjustProjectSettings { settings =>
      settings.connectionFileHash = currentConnectionFileHash
    }

    runImportAndVerify(
      configChangedNotification = true,
      serverConfig = BspConfigFile(getTestProjectPath.resolve(".bsp/sbt.json"))
    )
  }

  def test_whenMultipleBrokenFiles_andConnectionFileChangedExternally_shouldShowNotificationAndAdjustServerConfig(): Unit = {
    createBrokenConnectionFile(fileName = "other.json")
    createBrokenConnectionFile(fileName = "sbt.json")

    adjustProjectSettings { settings =>
      settings.bspConfigGenerated = true
      settings.connectionFileHash = connectionFileHashDifferentFromCurrent
    }

    runImportAndVerify(
      configChangedNotification = true,
      bspConfigGenerated = true,
      serverConfig = BspConfigFile(getTestProjectPath.resolve(".bsp/sbt.json"))
    )
  }

  def test_whenMultipleBrokenFiles_andConnectionFileNotChangedExternally_shouldAdjustServerConfigAndNotShowNotification(): Unit = {
    createBrokenConnectionFile(fileName = "other.json")
    createBrokenConnectionFile(fileName = "sbt.json")

    adjustProjectSettings { settings =>
      settings.bspConfigGenerated = true
      settings.connectionFileHash = currentConnectionFileHash
    }

    runImportAndVerify(
      bspConfigGenerated = true,
      serverConfig = BspConfigFile(getTestProjectPath.resolve(".bsp/sbt.json"))
    )
  }

  private def currentConnectionFileHash: Int =
    BspConnectionConfig.workspaceBspConfigsHash(getTestProjectPath)

  /**
   * By setting the connection file hash to a random value instead of the current hash,
   * we simulate a situation where the connection file was modified externally between BSP
   * server startups. In such a case, the connection file hash stored in the settings differs from the hash
   * calculated before the server starts, and it's used to decide whether the "config changed" notification should be displayed.
   *
   * @see [[org.jetbrains.bsp.protocol.BspConfigRegeneration.runGeneration]]
   */
  private def connectionFileHashDifferentFromCurrent: Int =
    Random.nextInt()

  private def adjustProjectSettings(configurator: BspProjectSettings => Unit): Unit =
    configurator(getCurrentExternalProjectSettings)

  /**
   * Creates a BSP connection file with valid JSON structure but an empty `argv`,
   * causing the BSP server startup to fail. This simulates a situation where the BSP connection file
   * contains invalid data e.g., points to a non-existent JDK.
   */
  private def createBrokenConnectionFile(fileName: String): Unit = {
    val file = getTestProjectPath.resolve(".bsp").resolve(fileName)
    Files.createDirectories(file.getParent)
    Files.writeString(file, """{"name":"sbt","version":"1.12.12","bspVersion":"2.1.0-M1","languages":["scala"],"argv":[]}""")
  }

  /**
   * Asserts that a "config changed" notification with the server startup failure reason was shown.
   *
   * @see [[BspConnectionFileNotificationService.showConfigChangedNotification]].
   */
  private def assertConfigChangedNotificationShown(notifications: Seq[Notification]): Unit = {
    val configNotifications = notifications.filter: notification =>
      notification.getTitle == BspBundle.message("bsp.protocol.config.file.regenerated") &&
        notification.getGroupId == BSP.NotificationGroup.getDisplayId

    if configNotifications.isEmpty then
      fail("Expected config changed notification, but none was shown")

    val notification = configNotifications.last
    val expectedContent = "regenerated because the BSP server failed to start"
    assertTrue(
      s"Expected notification content to contain '$expectedContent', but got: ${notification.getContent}",
      notification.getContent.contains(expectedContent)
    )
  }

  /**
   * Imports the project and verifies the outcome:
   *  - the current project structure matches the expected one
   *  - if `configChangedNotification` is `true`, a "config changed" notification was shown
   *  - no unexpected warning/error notifications were emitted
   *  - BSP project settings match the expected values after regeneration.
   */
  private def runImportAndVerify(
    configChangedNotification: Boolean = false,
    serverConfig: BspServerConfig = AutoConfig,
    bspConfigGenerated: Boolean = false,
    autoRegenerate: Boolean = false
  ): Unit = {
    val notificationsCollector = CollectingNotificationsListener.subscribeOnAllTypes(getMyProject)

    importProject(false)
    assertSimpleProjectStructure()

    val notifications = notificationsCollector.getNotifications

    if configChangedNotification then
      assertConfigChangedNotificationShown(notifications)

    val expectedEmptyNotifications =
      if configChangedNotification then
        notifications.filter: n =>
          n.getType == NotificationType.WARNING || n.getType == NotificationType.ERROR
      else
        notifications

    projectStructureAssertions.assertNoNotificationsShown(expectedEmptyNotifications)

    val settings = BspUtil.getBspProjectSettings(getMyProject, getTestProjectPath).orNull
    assertNotNull("BspProjectSettings should not be null", settings)
    assertNotNull("connectionFileHash should not be null", settings.connectionFileHash)
    assertEquals("Unexpected serverConfig", serverConfig, settings.serverConfig)
    assertEquals("Unexpected bspConfigGenerated", bspConfigGenerated, settings.bspConfigGenerated)
    assertEquals("Unexpected autoRegenerateBspConfigOnServerStartup", autoRegenerate, settings.autoRegenerateBspConfigOnServerStartup)
  }

  private def assertSimpleProjectStructure(): Unit = {
    val scalaLibraries = BspProjectStructureImportingTestUtils.expectedScalaLibraryWithScalaSdk("2.13.14", useScalaSdkExtraClasspath = true)

    val expectedProject = new project("simple") {
      libraries := scalaLibraries
      libraries.inexactMatch()

      modules := Seq(
        new module("simple") {
          libraryDependencies := BspProjectStructureImportingTestUtils.expectedLibraryDependencies(scalaLibraries, "simple")
          sources := Seq("src/main/scala", "src/main/java")
          testSources := Seq("src/test/scala", "src/test/java")
          resources := Seq("src/main/resources")
          testResources := Seq("src/test/resources")
          excluded := Seq("target", ".bloop", ".bsp")
        },
        new module("simple-build") {
          sources := Nil
          testSources := Nil
          resources := Nil
          testResources := Nil
          excluded := Nil
        }
      )
    }

    given ProjectStructureComparisonContext = ProjectStructureComparisonContext.Implicit.default(using getMyProject)
    assertProjectsEqual(expectedProject, getMyProject)
  }
}