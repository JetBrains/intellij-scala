package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.{EdtTestUtil, HeavyPlatformTestCase, PlatformTestUtil}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtForTestsSetupOps
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.junit.Assert._
import org.scalafmt.dynamic.ScalafmtVersion

import java.awt.Component
import java.nio.file.{Files, Path}
import javax.swing.{JButton, JComponent, JLabel}
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

  def testShowConfiguredFormatterVersion(): Unit =
    checkResolvedVersion("version = 2.7.5", "2.7.5")

  def testShowDefaultFormatterVersionWhenConfigurationOmitsVersion(): Unit =
    checkResolvedVersion("maxColumn = 100", s"$DefaultVersion (${ScalaBundle.message("scalafmt.default")})")

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

  private def checkCreateConfiguration(configPath: String, expectedFileName: String): Unit = {
    val expectedText = "version = 2.7.5"
    val settings = createSettings(configPath)

    val (panel, createButton, expectedPath) = EdtTestUtil.runInEdtAndGet(() => {
      // Add a physical content root so the panel can resolve relative configuration paths;
      // an empty heavy project has no directory discoverable through ProjectUtil.guessProjectDir.
      val projectRoot = createTestProjectStructure()
      val expectedPath = Path.of(projectRoot.getPath).resolve(expectedFileName)
      assertFalse("The configuration must not exist before clicking Create", Files.exists(expectedPath))
      val panel = createPanel(settings)
      val createButton = findCreateButton(panel.getPanel)
      assertMissingConfigurationUi(panel, createButton)
      (panel, createButton, expectedPath)
    })

    // Non-modal Settings dispatches button actions on EDT without an implicit write-intent lock.
    EdtTestUtil.runInEdtAndWait(() => {
      assertFalse("The test must dispatch Create without an implicit write-intent lock", ApplicationManager.getApplication.isWriteIntentLockAcquired)
      createButton.doClick(0)
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
    assertEquals("The preview should contain the newly created configuration", expectedText, inReadAction(panel.getEditor.getDocument.getText))
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
