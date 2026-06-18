package org.jetbrains.sbt.project.versionNotifications

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.notification.{Notification, NotificationAction, NotificationType, NotificationsManager}
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionUiKind, AnActionEvent, DataContext}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.{IdeaProjectTestFixture, IdeaTestFixtureFactory}
import com.intellij.testFramework.{PlatformTestUtil, ServiceContainerUtil, StartupActivityTestUtil, TestApplicationManager, UsefulTestCase}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.jetbrains.sbt.project.fixture.TestProjectRootFixture
import org.jetbrains.sbt.project.versionNotifications.utils.CapturingBrowserLauncher
import org.jetbrains.sbt.{SbtTestDataUtils, SbtVersion}
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.annotation.nowarn
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

/**
 * Tests for [[LegacySbtVersionProjectNotification]].
 *
 * The Build Tool Window counterpart lives in
 * [[LegacySbtVersionBuildToolWindowWarningTestBase]].
 *
 * In this test we extend UsefulTestCase and manually create a project fixture to control when the project is created and opened.
 * See [[createAndOpenIntelliJProjectAndWaitForSmartMode]]
 */
class LegacySbtVersionProjectNotificationTest extends UsefulTestCase {

  import Assertions.*
  import NotificationActionUtils.*

  // The listeners in LegacySbtVersionProjectNotification core logic require background thread
  override def runInDispatchThread: Boolean = false

  private var testProjectRootFixture: TestProjectRootFixture = uninitialized
  private var projectFixture: IdeaProjectTestFixture = uninitialized

  private def getProject = projectFixture.getProject

  override def setUp(): Unit = {
    super.setUp()

    // We delay project fixture initialization to the test cases bodies,
    // However, we still need the application to be initialized before first project is created and opened
    // In order first shown notification is registered properly
    initApplication()

    val testDataProject = Path.of(SbtTestDataUtils.resolveRelativePath("sbt-shell-runtime-tests/testdata/sbt/shell/sbtTestRunTest_0_13"))
    val copyOptions = TestProjectCopyOptions(copyToTemporaryDir = true, deleteTempDirectoryOnTestProcessShutDown = true)
    testProjectRootFixture = new TestProjectRootFixture(testDataProject, copyOptions)
    testProjectRootFixture.copyTestDataProjectToTempDirIfNeeded()
  }

  private def initApplication(): Unit = {
    // ignore the result, just trigger the initialization
    TestApplicationManager.getInstance
  }

  override def tearDown(): Unit = {
    // NOTE: it's expected that the project fixture is invoked in test body explicitly
    // If the `projectFixture` is null, then most likely the test failed before it was initialised
    try {
      if (projectFixture != null) {
        invokeAndWait {
          projectFixture.tearDown()
          projectFixture = null
        }
      }
    } finally {
      super.tearDown()
    }
  }

  /**
   * This should trigger the legacy warning notification automatically.
   *
   * We do this in a separate statement in each test in order to have time to make some initialisation for the proejct content.
   *
   * Without this, the project activities will be triggered too early, before the test is even started.
   */
  private def createAndOpenIntelliJProjectAndWaitForSmartMode(): Unit = {
    projectFixture = createUninitialisedProjectFixture()

    // This will trigger project instance creation and opening
    projectFixture.setUp()
    val project = projectFixture.getProject

    waitForProjectStartupActivities(project)

    invokeAndWait {
      UIUtil.dispatchAllInvocationEvents()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }

  private def createUninitialisedProjectFixture(): IdeaProjectTestFixture = {
    val factory = IdeaTestFixtureFactory.getFixtureFactory
    val projectPath = testProjectRootFixture.testProjectPath
    val parentPath = projectPath.getParent
    val name = projectPath.getFileName.toString
    val builder = factory.createFixtureBuilder(name, parentPath, true)
    builder.getFixture
  }

  //noinspection ScalaDeprecation
  @nowarn("cat=deprecation")
  private def waitForProjectStartupActivities(project: Project): Unit = {
    StartupActivityTestUtil.waitForProjectActivitiesToComplete(project)
    DumbService.getInstance(project).waitForSmartMode()
  }

  private val DefaultLegacySbtVersion = SbtVersion("0.13.18")

  def test_LegacySbtVersion_ShouldShowProjectNotification(): Unit = {
    writeBuildProperties(s"sbt.version=$DefaultLegacySbtVersion")
    createAndOpenIntelliJProjectAndWaitForSmartMode()
    assertSingleLegacySbtNotificationShownWithContent()
  }

  def test_LegacySbtVersion_ShouldShowProjectNotificationOnlyOnce(): Unit = {
    writeBuildProperties(s"sbt.version=$DefaultLegacySbtVersion")

    createAndOpenIntelliJProjectAndWaitForSmartMode()

    // Yes, do it 2 times here
    LegacySbtVersionProjectNotification.onProjectLoadedFromUnknownSource(getProject)
    LegacySbtVersionProjectNotification.onProjectLoadedFromUnknownSource(getProject)

    assertSingleLegacySbtNotificationShownWithContent()
  }

  def test_Sbt1_ShouldNotShowProjectNotification(): Unit = {
    writeBuildProperties(s"sbt.version=${SbtVersion.Latest.Sbt_1}")
    createAndOpenIntelliJProjectAndWaitForSmartMode()
    assertNoWarningsOrErrorsShown()
  }

  def test_Sbt2_ShouldNotShowProjectNotification(): Unit = {
    writeBuildProperties(s"sbt.version=${SbtVersion.Latest.Sbt_2}")
    createAndOpenIntelliJProjectAndWaitForSmartMode()
    assertNoWarningsOrErrorsShown()
  }

  // Locks in the current behavior of `SbtVersion.isSbt0`, which is `minor.startsWith("0")` — sbt 0.12.x is also flagged as legacy.
  // Regression guard: tightening the predicate to only 0.13 must update this test.
  def test_OlderLegacySbtVersion_ShouldShowProjectNotification(): Unit = {
    val Sbt012Version = SbtVersion("0.12.4")
    writeBuildProperties(s"sbt.version=$Sbt012Version")
    createAndOpenIntelliJProjectAndWaitForSmartMode()
    assertSingleLegacySbtNotificationShownWithContent(Sbt012Version)
  }

  def test_BadBuildProperties_MissingFile_ShouldNotShowProjectNotification(): Unit = {
    Files.delete(buildPropertiesPath)
    createAndOpenIntelliJProjectAndWaitForSmartMode()
    assertNoWarningsOrErrorsShown()
  }

  def test_BadBuildProperties_NoSbtVersion_ShouldNotShowProjectNotification(): Unit = {
    writeBuildProperties("# no sbt.version here")
    createAndOpenIntelliJProjectAndWaitForSmartMode()
    assertNoWarningsOrErrorsShown()
  }

  def test_BadBuildProperties_BlankSbtVersion_ShouldNotShowProjectNotification(): Unit = {
    writeBuildProperties("sbt.version=")
    createAndOpenIntelliJProjectAndWaitForSmartMode()
    assertNoWarningsOrErrorsShown()
  }

  def test_BadBuildProperties_UnparseableSbtVersion_ShouldNotShowProjectNotification(): Unit = {
    writeBuildProperties("sbt.version=NOT_A_VERSION")
    createAndOpenIntelliJProjectAndWaitForSmartMode()
    assertNoWarningsOrErrorsShown()
  }

  // Locks in the test-only seam that the other "shown once per session" tests rely on: clearing the session-state Key
  // must allow the warning to fire again. Regression guard for `clearShownInCurrentSession`.
  def test_LegacySbtVersion_ClearedSessionState_ShouldShowProjectNotificationAgain(): Unit = {
    writeBuildProperties(s"sbt.version=$DefaultLegacySbtVersion")

    createAndOpenIntelliJProjectAndWaitForSmartMode()

    clearLegacySbtWarningSessionState()

    // Have to use this as we can't open the project 2 times
    LegacySbtVersionProjectNotification.onProjectLoadedFromUnknownSource(getProject)

    val notifications = getCollectedWarningAndErrorNotifications
    assertEquals(2, notifications.size)
    notifications.foreach(assertLegacySbtVersionNotificationContent(_, DefaultLegacySbtVersion))
  }

  def test_NotificationActions_OpenMigrationGuideAction_ShouldBrowseMigrationGuideUrl(): Unit = {
    val expectedMigrationGuideUrl = "https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html"

    val capturingLauncher = new CapturingBrowserLauncher
    ServiceContainerUtil.replaceService(
      ApplicationManager.getApplication,
      classOf[BrowserLauncher],
      capturingLauncher,
      getTestRootDisposable,
    )

    writeBuildProperties(s"sbt.version=$DefaultLegacySbtVersion")
    createAndOpenIntelliJProjectAndWaitForSmartMode()

    val notification = assertSingleLegacySbtNotificationShownWithContent()

    invokeNotificationAction(
      notification,
      "Open migration guide",
    )

    assertEquals(
      "Expected the migration guide URL to be passed to BrowserLauncher.browse",
      Seq(expectedMigrationGuideUrl),
      capturingLauncher.getCapturedUrls,
    )
  }

  def test_NotificationActions_OpenBuildPropertiesAction_BuildPropertiesExists_ShouldOpenFileInEditor(): Unit = {
    val sbtVersion = SbtVersion("0.13.0")
    writeBuildProperties(s"sbt.version=$sbtVersion")
    createAndOpenIntelliJProjectAndWaitForSmartMode()

    val notification = assertSingleLegacySbtNotificationShownWithContent(sbtVersion)

    invokeNotificationAction(
      notification,
      "Open build.properties",
    )

    val openFiles = FileEditorManager.getInstance(getProject).getOpenFiles
    assertTrue(
      s"Expected $buildPropertiesPath to be opened in the editor, got: ${openFilesText(openFiles)}",
      openFiles.exists(vf => vf.toNioPath.toAbsolutePath.normalize() == buildPropertiesPath.toAbsolutePath.normalize()),
    )
  }

  private def openFilesText(openFiles: Array[VirtualFile]): String =
    openFiles.map(_.getPath).mkString("[", ", ", "]")

  def test_NotificationActions_OpenBuildPropertiesAction_BuildPropertiesMissing_ShouldDoNothing(): Unit = {
    // Materialize the notification *before* deleting build.properties — the action's
    // `createBuildPropertiesOpenFileDescriptor` is re-evaluated each time it fires.
    writeBuildProperties(s"sbt.version=$DefaultLegacySbtVersion")
    createAndOpenIntelliJProjectAndWaitForSmartMode()
    val notification = assertSingleLegacySbtNotificationShownWithContent()

    Files.delete(buildPropertiesPath)

    invokeNotificationAction(
      notification,
      "Open build.properties",
    )

    val openFiles = FileEditorManager.getInstance(getProject).getOpenFiles
    assertFalse(
      s"Expected no editor opened when build.properties is absent, got: ${openFilesText(openFiles)}",
      openFiles.exists(vf => vf.toNioPath.toAbsolutePath.normalize() == buildPropertiesPath.toAbsolutePath.normalize()),
    )
  }

  private lazy val buildPropertiesPath: Path =
    testProjectRootFixture.testProjectPath.resolve("project").resolve("build.properties")

  private def writeBuildProperties(text: String): Unit =
    Files.writeString(buildPropertiesPath, text, StandardCharsets.UTF_8)

  private def clearLegacySbtWarningSessionState(): Unit =
    LegacySbtVersionProjectNotification.clearShownInCurrentSession(getProject)

  private def getCollectedWarningAndErrorNotifications: Seq[Notification] =
    getCollectedWarningAndErrorNotifications(getProject)

  private def getCollectedWarningAndErrorNotifications(project: Project): Seq[Notification] = {
    val manager = NotificationsManager.getNotificationsManager
    val notifications = manager.getNotificationsOfType(classOf[Notification], project).toSeq
    notifications.filter(n => WarningOrErrorNotificationTypeSet.contains(n.getType))
  }

  private val WarningOrErrorNotificationTypeSet = Set(NotificationType.WARNING, NotificationType.ERROR)

  private object Assertions {

    def assertSingleLegacySbtNotificationShownWithContent(sbtVersion: SbtVersion = DefaultLegacySbtVersion): Notification = {
      val notifications = getCollectedWarningAndErrorNotifications
      assertEquals("Expected exactly one legacy sbt notification", 1, notifications.size)

      val notification = notifications.head
      assertLegacySbtVersionNotificationContent(notification, sbtVersion)
      notification
    }

    def assertLegacySbtVersionNotificationContent(notification: Notification, sbtVersion: SbtVersion): Unit = {
      assertEquals(NotificationType.WARNING, notification.getType)
      assertEquals(s"Legacy sbt version $sbtVersion detected", notification.getTitle)
      assertEquals(
        """It appears that this project is using sbt 0.13.x.
          |This version has been deprecated since 2014 and officially reached end-of-life in 2018.
          |Using it may lead to compatibility issues, missing out on important features, performance improvements, and security fixes.
          |
          |Please upgrade to the latest sbt version by updating the sbt.version value in the build.properties file.""".stripMargin,
        notification.getContent,
      )

      val actionTexts = notification.getActions.asScala.map(_.getTemplateText).toSeq
      assertEquals(
        Seq(
          "Open migration guide",
          "Open build.properties",
        ),
        actionTexts,
      )
    }

    def assertNoWarningsOrErrorsShown(): Unit = {
      val notifications = getCollectedWarningAndErrorNotifications
      assertTrue(
        s"""Expected no notifications, but the following notifications were shown:
           |${notifications.map(renderNotification).mkString("\n")}""".stripMargin,
        notifications.isEmpty,
      )
    }

    private def renderNotification(n: Notification): String =
      s"""Group id: ${n.getGroupId}
         |Title: ${n.getTitle}
         |Subtitle: ${n.getSubtitle}
         |Content: ${n.getContent}""".stripMargin
  }

  private object NotificationActionUtils {
    def invokeNotificationAction(notification: Notification, actionTitle: String): Unit = {
      val action = getActionByTitleOrFail(notification, actionTitle)
      invokeAndWait {
        val event = AnActionEvent.createEvent(action, DataContext.EMPTY_CONTEXT, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
        action.actionPerformed(event, notification)
      }
    }

    private def getActionByTitleOrFail(notification: Notification, actionTitle: String) = notification.getActions.asScala.find(_.getTemplateText == actionTitle) match {
      case Some(a: NotificationAction) => a
      case Some(other) =>
        throw new AssertionError(s"Action '$actionTitle' is not a NotificationAction: ${other.getClass.getName}")
      case None =>
        val available = notification.getActions.asScala.map(_.getTemplateText).mkString("[", ", ", "]")
        throw new AssertionError(s"Action '$actionTitle' not found among $available")
    }
  }
}
