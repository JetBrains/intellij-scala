package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileChooser._
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.{EdtTestUtil, HeavyPlatformTestCase, PlatformTestUtil, ServiceContainerUtil}
import com.intellij.util.Consumer
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction}
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtForTestsSetupOps
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.junit.Assert._
import org.scalafmt.dynamic.ScalafmtVersion

import java.awt.Component
import java.nio.file.{Files, Path}
import java.util.concurrent.CompletableFuture
import javax.swing.{JButton, JComponent, JLabel, JTextField}
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

// Use a real project directory: an in-memory fixture cannot exercise NIO creation and VFS refresh together.
class ScalaFmtSettingsPanelTest extends HeavyPlatformTestCase {

  override def runInDispatchThread(): Boolean = false

  override def setUp(): Unit = {
    super.setUp()

    ScalaFmtForTestsSetupOps.ensureDownloaded(DefaultVersion, ScalafmtVersion(2, 7, 5))
  }

  def testCreateDefaultConfigurationWithoutWriteIntentLock(): Unit =
    checkCreateConfiguration(configPath = "", expectedFileName = ".scalafmt.conf")

  def testCreateCustomConfigurationWithoutWriteIntentLock(): Unit =
    checkCreateConfiguration(configPath = "custom-scalafmt.conf", expectedFileName = "custom-scalafmt.conf")

  def testCreateLoadsConfigurationCreatedExternallyAfterMissingFileCheck(): Unit =
    checkCreateConfiguration(
      configPath = "",
      expectedFileName = ".scalafmt.conf",
      externallyCreatedText = Some(s"version = $DefaultVersion\nmaxColumn = 123")
    )

  def testShowConfiguredFormatterVersion(): Unit =
    checkResolvedVersion("version = 2.7.5", "2.7.5")

  def testShowDefaultFormatterVersionWhenConfigurationOmitsVersion(): Unit =
    checkResolvedVersion("maxColumn = 100", s"$DefaultVersion (${ScalaBundle.message("scalafmt.default")})")

  def testBrowsePreselectsRelativeConfiguration(): Unit =
    checkBrowseSelection("nested/custom.conf", "nested/custom.conf")

  def testBrowsePreselectsDefaultConfiguration(): Unit =
    checkBrowseSelection("", ".scalafmt.conf")

  def testBrowsePreservesAbsoluteConfigurationSelection(): Unit =
    checkBrowseSelection("nested/custom.conf", "nested/custom.conf", absolute = true)

  def testBrowseSelectionUpdatesPathAndPreview(): Unit =
    checkBrowseSelection("first.conf", "first.conf", chooseAnother = true)

  @nowarn("cat=deprecation")
  def testBrowsePreselectsExistingParentOfMissingConfiguration(): Unit = {
    val fixture = EdtTestUtil.runInEdtAndGet(() => {
      val fixture = createBrowseFixture("nested/missing/custom.conf", "nested/existing.conf")
      fixture.pathField.getButton.doClick(0)
      fixture
    })
    EdtTestUtil.runInEdtAndWait(() => {
      PlatformTestUtil.waitWithEventsDispatching("Browse should open for a missing configuration", () => fixture.selected.isDone, 10)
      assertEquals("Browse must retain the platform's nearest-existing-parent fallback",
        Some(fixture.expectedFile.getParent), fixture.selected.get())
    }, false)
  }

  def testDisposalWhileResolvingBrowseSelectionDoesNotOpenChooser(): Unit =
    checkPendingBrowseDoesNotOpenChooser("panel disposal") { fixture =>
      Disposer.dispose(fixture.panel)
    }

  def testPathChangeWhileResolvingBrowseSelectionDoesNotOpenChooser(): Unit =
    checkPendingBrowseDoesNotOpenChooser("a configuration path change") { fixture =>
      val previousPath = fixture.pathField.getText
      fixture.pathField.setText(fixture.chosenFile.getPath)
      assertNotEquals("The pending Browse lookup must refer to a different configuration path",
        previousPath, fixture.pathField.getText)
    }

  def testProjectChangeWhileResolvingBrowseSelectionDoesNotOpenChooser(): Unit =
    checkPendingBrowseDoesNotOpenChooser("a project change") { fixture =>
      val previousPath = fixture.pathField.getText
      val anotherProject = ProjectManager.getInstance().getDefaultProject
      assertNotSame("The panel must be rebound to a different project", getProject, anotherProject)
      fixture.panel.onProjectSet(anotherProject)
      assertEquals("Keep the path unchanged so only the project guard can reject the pending lookup",
        previousPath, fixture.pathField.getText)
    }

  @nowarn("cat=deprecation")
  private def checkPendingBrowseDoesNotOpenChooser(changeDescription: String)(change: BrowseFixture => Unit): Unit = {
    val fixture = EdtTestUtil.runInEdtAndGet(() => {
      // Keep the lookup pending until its selection or lifetime changes; no timing assumptions are needed.
      inWriteAction {
        val fixture = createBrowseFixture("nested/custom.conf", "nested/custom.conf")
        fixture.pathField.getButton.doClick(0)
        assertFalse("Browse must resolve its initial selection asynchronously", fixture.selected.isDone)
        // A second Browse click would coalesce requests and bypass the stale-selection guard under test.
        change(fixture)
        fixture
      }
    })
    EdtTestUtil.runInEdtAndWait(() => {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      assertFalse(s"A pending Browse lookup must not open a chooser after $changeDescription", fixture.selected.isDone)
    }, false)
  }

  @nowarn("cat=deprecation")
  private def checkBrowseSelection(configPath: String, fileName: String, absolute: Boolean = false, chooseAnother: Boolean = false): Unit = {
    val fixture = EdtTestUtil.runInEdtAndGet(() => {
      val fixture = createBrowseFixture(configPath, fileName, absolute, chooseAnother)
      assertEquals("Browse must work before the asynchronous preview is delivered", "", fixture.panel.getEditor.getDocument.getText)
      fixture.pathField.getButton.doClick(0)
      fixture
    })

    EdtTestUtil.runInEdtAndWait(() => {
      PlatformTestUtil.waitWithEventsDispatching(s"Browse should open for '$configPath'", () => fixture.selected.isDone, 10)
      assertEquals(s"Browse should preselect the configured file for '$configPath' (absolute=$absolute)",
        Some(fixture.expectedFile), fixture.selected.get())
      if (chooseAnother) {
        PlatformTestUtil.waitWithEventsDispatching("A file chosen through Browse should load into the preview",
          () => fixture.panel.getEditor.getDocument.getText == "version = 2.7.5\nmaxColumn = 120", 10)
        // The platform's Browse action shows the presentable path, which uses backslashes on Windows.
        assertEquals("Browse should copy the chosen file's path into Settings", fixture.chosenFile.getPresentableUrl, fixture.pathField.getText)
      }
    }, false)
  }

  private case class BrowseFixture(
    panel: ScalaFmtSettingsPanel,
    pathField: TextFieldWithBrowseButton,
    expectedFile: VirtualFile,
    chosenFile: VirtualFile,
    selected: CompletableFuture[Option[VirtualFile]]
  )

  private def createBrowseFixture(configPath: String, fileName: String, absolute: Boolean = false, chooseAnother: Boolean = false): BrowseFixture = {
    val projectRoot = createTestProjectStructure()
    val root = Path.of(projectRoot.getPath)
    val path = root.resolve(fileName)
    Files.createDirectories(path.getParent)
    Files.writeString(path, "version = 2.7.5")
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    assertNotNull("Browse must receive an existing physical configuration", file)
    val anotherPath = root.resolve("another.conf")
    Files.writeString(anotherPath, "version = 2.7.5\nmaxColumn = 120")
    val another = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(anotherPath)
    assertNotNull("The configuration returned by the chooser must exist", another)
    val panel = createPanel(createSettings(if (absolute) path.toString else configPath))

    // Keep the installed browse action and platform path conversion real; replace only the dialog.
    val selected = new CompletableFuture[Option[VirtualFile]]()
    val factory = new RecordingChooserFactory(FileChooserFactory.getInstance(), getProject, selected,
      if (chooseAnother) Some(another) else None)
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication, classOf[FileChooserFactory], factory, getTestRootDisposable)
    val fields = UIUtil.findComponentsOfType(panel.getPanel, classOf[TextFieldWithBrowseButton]).asScala
    assertEquals("The panel should expose one configuration Browse field", 1, fields.size)
    BrowseFixture(panel, fields.head, file, another, selected)
  }

  private class RecordingChooserFactory(
    delegate: FileChooserFactory,
    expectedProject: Project,
    selected: CompletableFuture[Option[VirtualFile]],
    choose: Option[VirtualFile]
  ) extends FileChooserFactory {
    override def createPathChooser(descriptor: FileChooserDescriptor, project: Project, parent: Component): PathChooserDialog =
      new PathChooserDialog {
        override def choose(initialFile: VirtualFile, consumer: Consumer[? >: java.util.List[VirtualFile]]): Unit = {
          assertSame("Browse must use the panel's bound project", expectedProject, project)
          assertTrue("The chooser must open on EDT", ApplicationManager.getApplication.isDispatchThread)
          selected.complete(Option(initialFile))
          // A cancelled chooser does not invoke its selection callback.
          RecordingChooserFactory.this.choose.foreach(file => consumer.consume(java.util.List.of(file)))
        }
      }

    override def createFileChooser(d: FileChooserDescriptor, p: Project, c: Component): FileChooserDialog = delegate.createFileChooser(d, p, c)
    override def createSaveFileDialog(d: FileSaverDescriptor, p: Project): FileSaverDialog = delegate.createSaveFileDialog(d, p)
    override def createSaveFileDialog(d: FileSaverDescriptor, c: Component): FileSaverDialog = delegate.createSaveFileDialog(d, c)
    override def createFileTextField(d: FileChooserDescriptor, showHidden: Boolean, disposable: Disposable): FileTextField =
      delegate.createFileTextField(d, showHidden, disposable)
    override def installFileCompletion(field: JTextField, d: FileChooserDescriptor, showHidden: Boolean, disposable: Disposable): Unit =
      delegate.installFileCompletion(field, d, showHidden, disposable)
  }

  private def checkResolvedVersion(configText: String, expectedVersion: String): Unit = {
    val panel = EdtTestUtil.runInEdtAndGet(() => {
      // The panel discovers relative config paths through the physical project's content root.
      val projectRoot = createTestProjectStructure()
      val configPath = Path.of(projectRoot.getPath).resolve(".scalafmt.conf")
      Files.writeString(configPath, configText)
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(configPath)
      createPanel(createSettings(""))
    })

    // Let the real asynchronous resolver finish without holding write intent while pumping EDT events.
    EdtTestUtil.runInEdtAndWait(() => {
      PlatformTestUtil.waitWithEventsDispatching(
        s"The panel should display formatter version '$expectedVersion'",
        () => UIUtil.findComponentsOfType(panel.getPanel, classOf[JLabel]).asScala.exists(_.getText == expectedVersion),
        10
      )
    }, false)
  }

  private def checkCreateConfiguration(
    configPath: String,
    expectedFileName: String,
    externallyCreatedText: Option[String] = None
  ): Unit = {
    val expectedText = externallyCreatedText.getOrElse("version = 2.7.5")
    val settings = createSettings(configPath)

    val (panel, createButton, expectedPath) = EdtTestUtil.runInEdtAndGet(() => {
      // Add a physical content root so the panel can resolve relative configuration paths;
      // an empty heavy project has no directory discoverable through ProjectUtil.guessProjectDir.
      val projectRoot = createTestProjectStructure()
      val expectedPath = Path.of(projectRoot.getPath).resolve(expectedFileName)
      assertFalse("The configuration must not exist before clicking Create", Files.exists(expectedPath))
      val panel = createPanel(settings)
      val createButton = findCreateButton(panel.getPanel)
      (panel, createButton, expectedPath)
    })

    EdtTestUtil.runInEdtAndWait(() => {
      PlatformTestUtil.waitWithEventsDispatching("A missing configuration should enable Create",
        () => isVisibleInPanel(createButton, panel.getPanel), 10)
      assertMissingConfigurationUi(panel, createButton)
    }, false)

    externallyCreatedText.foreach { text =>
      // Reproduce external creation after the missing-file check in a fixed order, without refreshing VFS.
      Files.writeString(expectedPath, text)
    }

    // Non-modal Settings dispatches button actions on EDT without an implicit write-intent lock.
    EdtTestUtil.runInEdtAndWait(() => {
      assertFalse("The test must dispatch Create without an implicit write-intent lock", ApplicationManager.getApplication.isWriteIntentLockAcquired)
      createButton.doClick(0)
      PlatformTestUtil.waitWithEventsDispatching("Create should load the configuration preview",
        () => panel.getEditor.getDocument.getText.nonEmpty, 10)
      assertCreatedConfigurationUi(panel, createButton, expectedText)
    }, false)

    assertCreatedConfigurationContents(expectedPath, expectedText)
  }

  private def createSettings(configPath: String): CodeStyleSettings = {
    // Keep these temporary settings out of CodeStyleSettingsManager's global registry;
    // new CodeStyleSettings() registers the instance globally.
    val settings = CodeStyle.createTestSettings()
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
    scalaSettings.SCALAFMT_CONFIG_PATH = configPath
    settings
  }

  private def createPanel(settings: CodeStyleSettings): ScalaFmtSettingsPanel = {
    val panel = new ScalaFmtSettingsPanel(settings)
    Disposer.register(getTestRootDisposable, panel)
    panel.getPanel
    panel.onProjectSet(getProject)
    panel
  }

  private def assertMissingConfigurationUi(panel: ScalaFmtSettingsPanel, createButton: JButton): Unit = {
    val component = panel.getPanel
    assertTrue("The Create action should be visible for a missing configuration", isVisibleInPanel(createButton, component))
    assertTrue("The Create action should be enabled for a missing configuration", createButton.isEnabled)
    assertFalse("The preview should be hidden for a missing configuration", isVisibleInPanel(panel.getEditor.getComponent, component))
    assertEquals("The configuration preview should initially be empty", "", panel.getEditor.getDocument.getText)
  }

  private def assertCreatedConfigurationUi(panel: ScalaFmtSettingsPanel, createButton: JButton, expectedText: String): Unit = {
    assertFalse("The Create action should disappear after creating the configuration", isVisibleInPanel(createButton, panel.getPanel))
    assertTrue("The configuration preview should become visible", isVisibleInPanel(panel.getEditor.getComponent, panel.getPanel))
    assertEquals("The preview should contain the configuration at the requested path", expectedText, inReadAction(panel.getEditor.getDocument.getText))
  }

  private def assertCreatedConfigurationContents(expectedPath: Path, expectedText: String): Unit = {
    assertEquals(s"The configuration should be written to $expectedPath", expectedText, Files.readString(expectedPath))
    inReadAction {
      // Do not refresh here: the Create action must make the expected file visible in VFS itself.
      val file = LocalFileSystem.getInstance().findFileByNioFile(expectedPath)
      assertNotNull("The configuration must be immediately visible in the project VFS", file)
      assertTrue("The created configuration must be a valid VFS file", file.isValid)
      val document = FileDocumentManager.getInstance().getDocument(file)
      assertNotNull("The created configuration must have a document", document)
      assertEquals("The configuration document should match the file written to disk", expectedText, document.getText)
    }
  }

  private def findCreateButton(component: JComponent): JButton = {
    val buttons = UIUtil.findComponentsOfType(component, classOf[JButton]).asScala
      .filter(_.getText == ScalaBundle.message("scalafmt.can.not.find.config.file.create.new"))
    assertEquals("Expected exactly one Create configuration action", 1, buttons.size)
    buttons.head
  }

  // The panel is not attached to a window, so isShowing cannot be used to check its visible state.
  private def isVisibleInPanel(component: Component, panel: JComponent): Boolean =
    component != null && component.isVisible &&
      ((component eq panel) || isVisibleInPanel(component.getParent, panel))
}
