package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.{ProgressIndicatorBase, ProgressIndicatorListener}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.{EdtTestUtil, HeavyPlatformTestCase, PlatformTestUtil}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.lang.formatting.ScalaFormattingBundle
import org.jetbrains.plugins.scala.settings.ScalaSettingsBundle
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction}
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.utils.ScalafmtConfigUtils
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalafmtSettingsConfigResolver.{Completion, LoadedConfig, Version}
import org.junit.Assert._
import org.scalafmt.dynamic.ScalafmtVersion

import java.awt.Component
import java.awt.event.{ActionListener, FocusEvent}
import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import javax.swing.{JButton, JCheckBox, JLabel}
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

class ScalaFmtSettingsPanelResolutionTest extends HeavyPlatformTestCase {
  override def runInDispatchThread(): Boolean = false

  def testBackgroundCompletionUpdatesVersionOnEdt(): Unit = {
    val fixture = createFixture()
    val updatedOnEdt = new AtomicBoolean(false)
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.versionLabel.addPropertyChangeListener("text", _ => {
        updatedOnEdt.set(ApplicationManager.getApplication.isDispatchThread)
      })
    })

    assertFalse("The resolver completion must originate outside EDT in this test", ApplicationManager.getApplication.isDispatchThread)
    fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
    assertVersion(fixture, "2.7.5")
    assertTrue("The panel must update Swing components on EDT", updatedOnEdt.get())
  }

  def testOlderCompletionCannotOverwriteNewConfigurationVersion(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).SCALAFMT_CONFIG_PATH = "b.conf"
      fixture.panel.resetImpl(fixture.settings)
    })

    fixture.resolver.complete("b.conf", ScalafmtVersion(3, 11, 5))
    assertVersion(fixture, "3.11.5")
    fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
    assertVersion(fixture, "3.11.5")
  }

  def testChangingConfigurationClearsPreviousVersionWhileResolving(): Unit = {
    val fixture = createFixture()
    fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
    assertVersion(fixture, "2.7.5")

    EdtTestUtil.runInEdtAndWait(() => {
      fixture.pathField.setText("b.conf")
      losePathFocus(fixture)
      assertEquals("Starting resolution of a new configuration must clear the previous formatter version",
        "", fixture.versionLabel.getText)
    })

    fixture.resolver.complete("b.conf", ScalafmtVersion(3, 11, 5))
    assertVersion(fixture, "3.11.5")
  }

  def testChoosingDefaultInvalidatesPendingConfigurationResolution(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).SCALAFMT_CONFIG_PATH = "missing.conf"
      fixture.panel.resetImpl(fixture.settings)
      flushUi()
      assertMissingConfiguration(fixture)
    })

    val defaultLabel = s"$DefaultVersion (${ScalaSettingsBundle.message("scalafmt.default")})"
    assertVersion(fixture, defaultLabel)
    fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
    assertVersion(fixture, defaultLabel)
  }

  def testCompletionAfterDisposalDoesNotUpdateVersion(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => Disposer.dispose(fixture.panel))

    fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
    assertVersion(fixture, "")
  }

  def testQueuedCompletionCannotOverwriteDefaultVersion(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
      assertEquals("The resolution result must stay queued until the current EDT action finishes",
        "", fixture.versionLabel.getText)

      // Invalidate after completion has been queued, before EDT can apply it.
      fixture.settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).SCALAFMT_CONFIG_PATH = "missing.conf"
      fixture.panel.resetImpl(fixture.settings)
    })

    assertVersion(fixture, s"$DefaultVersion (${ScalaSettingsBundle.message("scalafmt.default")})")
  }

  def testDisablingFormatterInvalidatesPendingConfigurationResolution(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).FORMATTER = ScalaCodeStyleSettings.INTELLIJ_FORMATTER
      fixture.panel.resetImpl(fixture.settings)
      assertTrue("Disabling Scalafmt should cancel the active worker", fixture.resolver.indicators.get("a.conf").isCanceled)
    })

    fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
    assertVersion(fixture, "")
  }

  def testInitiallyDisabledPanelKeepsUnloadedPreviewHidden(): Unit = {
    val fixture = createFixture(formatterEnabled = false)
    EdtTestUtil.runInEdtAndWait(() => {
      assertEquals("An initially disabled panel should not resolve a configuration", 0, fixture.resolver.requestCount.get())
      assertEquals("An unloaded preview should have no configuration text", "", fixture.panel.getEditor.getDocument.getText)
      assertFalse("An initially disabled panel should hide its unloaded preview",
        isVisibleInPanel(fixture.panel.getEditor.getComponent, fixture.panel.getPanel))
    })
  }

  def testUnboundPanelDoesNotOfferCreateBeforeProjectLookup(): Unit = {
    val fixture = createFixture(bindProject = false)
    EdtTestUtil.runInEdtAndWait(() => {
      val component = fixture.panel.getPanel
      assertFalse("An unbound panel must not claim that a configuration is missing",
        isVisibleInPanel(fixture.createButton, component))
      assertFalse("An unbound panel must not display an unloaded preview",
        isVisibleInPanel(fixture.panel.getEditor.getComponent, component))
      assertEquals("File lookup must wait until a project is bound", 0, fixture.resolver.requestCount.get())
    })
  }

  @nowarn("cat=deprecation")
  def testProjectBindingPreservesInstalledListeners(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      val unrelatedListener: ActionListener = _ => ()
      fixture.pathField.addActionListener(unrelatedListener)
      val focusListeners = fixture.pathField.getTextField.getFocusListeners.toSeq
      val actionListeners = fixture.pathField.getButton.getActionListeners.toSeq

      fixture.panel.onProjectSet(getProject)
      fixture.panel.onProjectSet(getProject)

      assertEquals("Project binding must preserve the existing focus listeners without adding more",
        focusListeners, fixture.pathField.getTextField.getFocusListeners.toSeq)
      assertEquals("Project binding must preserve browse actions, including listeners owned by other code",
        actionListeners, fixture.pathField.getButton.getActionListeners.toSeq)
    })
  }

  def testOnePathChangeReloadsOnceAfterProjectRebinding(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.panel.onProjectSet(getProject)
      fixture.panel.onProjectSet(getProject)
      val requestsBefore = fixture.resolver.requestCount.get()

      fixture.pathField.setText("b.conf")
      losePathFocus(fixture)

      assertEquals("One path change must issue one configuration resolution request",
        requestsBefore + 1, fixture.resolver.requestCount.get())
      assertEquals("Changing the path should load the selected configuration into the preview",
        "version = 3.11.5", fixture.panel.getEditor.getDocument.getText)
      assertTrue("An existing configuration should have a visible preview",
        isVisibleInPanel(fixture.panel.getEditor.getComponent, fixture.panel.getPanel))
      assertFalse("The Create action should be hidden for an existing configuration",
        isVisibleInPanel(fixture.createButton, fixture.panel.getPanel))
    })
    fixture.resolver.complete("b.conf", ScalafmtVersion(3, 11, 5))
    assertVersion(fixture, "3.11.5")
  }

  def testUnchangedPathPreservesPreviewEdits(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      val editedText = "version = 2.7.5\nmaxColumn = 120"
      inWriteAction(fixture.panel.getEditor.getDocument.setText(editedText))
      val requestsBefore = fixture.resolver.requestCount.get()

      losePathFocus(fixture)

      assertEquals("Leaving an unchanged path must preserve unapplied preview edits",
        editedText, fixture.panel.getEditor.getDocument.getText)
      assertTrue("Preserved preview edits should still make the panel modified", fixture.panel.isModified(fixture.settings))
      assertEquals("Leaving an unchanged path must not resolve the same configuration again",
        requestsBefore, fixture.resolver.requestCount.get())
    })
  }

  def testMissingPathClearsPreviewOnFocusLoss(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.pathField.setText("missing.conf")
      losePathFocus(fixture)
      flushUi()
      assertMissingConfiguration(fixture)
    })
    assertVersion(fixture, s"$DefaultVersion (${ScalaSettingsBundle.message("scalafmt.default")})")
  }

  def testResetReloadsSameFileAndDiscardsPreviewEdits(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      val updatedText = "version = 2.7.5\nmaxColumn = 90"
      inWriteAction {
        FileDocumentManager.getInstance().getDocument(fixture.projectRoot.findChild("a.conf")).setText(updatedText)
        fixture.panel.getEditor.getDocument.setText("unapplied preview edits")
      }
      val requestsBefore = fixture.resolver.requestCount.get()

      fixture.panel.resetImpl(fixture.settings)
      flushUi()

      assertEquals("Reset must reload current file contents even when the path is unchanged",
        updatedText, fixture.panel.getEditor.getDocument.getText)
      assertFalse("Reset should discard preview edits and establish a new baseline", fixture.panel.isModified(fixture.settings))
      assertEquals("Reset should resolve the reloaded configuration once",
        requestsBefore + 1, fixture.resolver.requestCount.get())
    })
  }

  def testApplySavesPreviewEditsToLoadedFile(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      val editedText = "version = 2.7.5\nmaxColumn = 120"
      inWriteAction(fixture.panel.getEditor.getDocument.setText(editedText))

      fixture.panel.apply(fixture.settings)

      assertEquals("Apply must save preview edits to the loaded configuration document",
        editedText, inReadAction(FileDocumentManager.getInstance().getDocument(fixture.projectRoot.findChild("a.conf")).getText))
      assertFalse("Saved preview edits should become the new baseline", fixture.panel.isModified(fixture.settings))
    })
  }

  def testApplyDoesNotWritePreviousPreviewEditsIntoNewFile(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      inWriteAction(fixture.panel.getEditor.getDocument.setText("version = 2.7.5\nmaxColumn = 120"))
      // Apply is a public entry point and must be correct even before a focus-loss event commits the new path.
      fixture.pathField.setText("b.conf")

      fixture.panel.apply(fixture.settings)
      flushUi()

      val expectedText = "version = 3.11.5"
      assertEquals("Apply must not copy edits belonging to the previous file into the newly selected file",
        expectedText, inReadAction(FileDocumentManager.getInstance().getDocument(fixture.projectRoot.findChild("b.conf")).getText))
      assertEquals("The preview should represent the newly selected file after Apply",
        expectedText, fixture.panel.getEditor.getDocument.getText)
      assertEquals("Apply should persist the newly selected path", "b.conf",
        fixture.settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).SCALAFMT_CONFIG_PATH)
    })
  }

  def testApplyingUnrelatedOptionDoesNotReloadConfiguration(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      val options = UIUtil.findComponentsOfType(fixture.panel.getPanel, classOf[JCheckBox]).asScala
        .filter(_.getText == ScalaSettingsBundle.message("scalafmt.settings.panel.show.warnings.when.formatting.invalid.code"))
      assertEquals("The panel should expose one invalid-code warning option", 1, options.size)
      val option = options.head
      option.setSelected(!option.isSelected)
      val requestsBefore = fixture.resolver.requestCount.get()

      fixture.panel.apply(fixture.settings)

      assertEquals("Apply must persist the changed warning option", option.isSelected,
        fixture.settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).SCALAFMT_SHOW_INVALID_CODE_WARNINGS)
      assertFalse("Changing a warning option must not cancel the active resolution",
        fixture.resolver.indicators.get("a.conf").isCanceled)
      assertEquals("Changing a warning option must not resolve the configuration again",
        requestsBefore, fixture.resolver.requestCount.get())
    })
  }

  def testLoadingHidesCreateUntilMissingFileIsKnown(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.resolver.holdLoads = true
      fixture.pathField.setText("missing.conf")
      losePathFocus(fixture)
      assertFalse("Create must stay hidden while the file lookup is pending",
        isVisibleInPanel(fixture.createButton, fixture.panel.getPanel))
      assertEquals("Loading a new file must clear the previous preview", "", fixture.panel.getEditor.getDocument.getText)

      fixture.resolver.load("missing.conf")
      flushUi()
      assertMissingConfiguration(fixture)
    })
  }

  def testReplacingRequestCancelsWorkerAndRejectsLatePreview(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.resolver.holdLoads = true
      fixture.pathField.setText("b.conf")
      losePathFocus(fixture)
      assertTrue("Selecting another configuration should cancel the previous worker",
        fixture.resolver.indicators.get("a.conf").isCanceled)
      fixture.pathField.setText("a.conf")
      losePathFocus(fixture)
      fixture.resolver.load("a.conf")
      flushUi()
      inWriteAction(fixture.panel.getEditor.getDocument.setText("new preview edits"))

      fixture.resolver.load("b.conf")
      fixture.resolver.complete("b.conf", ScalafmtVersion(3, 11, 5))
      flushUi()
      assertEquals("An obsolete loading callback must not overwrite edits in the current preview",
        "new preview edits", fixture.panel.getEditor.getDocument.getText)
      assertEquals("An obsolete resolution callback must not update the current version", "", fixture.versionLabel.getText)
    })
  }

  def testResolutionDoesNotOverwritePreviewEdits(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      inWriteAction(fixture.panel.getEditor.getDocument.setText("edits made during download"))
    })
    fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
    assertVersion(fixture, "2.7.5")
    EdtTestUtil.runInEdtAndWait(() => {
      assertEquals("Finishing formatter resolution must leave the editable preview alone",
        "edits made during download", fixture.panel.getEditor.getDocument.getText)
    })
  }

  def testApplyDuringLoadingPersistsPathWithoutWaiting(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.resolver.holdLoads = true
      fixture.pathField.setText("missing.conf")
      losePathFocus(fixture)
      val requestsBefore = fixture.resolver.requestCount.get()
      fixture.panel.apply(fixture.settings)
      assertEquals("Apply must persist the requested path before file lookup completes", "missing.conf",
        fixture.settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).SCALAFMT_CONFIG_PATH)
      // A path committed by focus loss already has an active request; Apply should keep it running.
      assertEquals("Apply must reuse an active request for the unchanged requested path",
        requestsBefore, fixture.resolver.requestCount.get())
      fixture.resolver.load("missing.conf")
      flushUi()
      assertMissingConfiguration(fixture)
    })
  }

  def testDisposalCancelsWorkerAndRejectsLatePreview(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.resolver.holdLoads = true
      fixture.pathField.setText("b.conf")
      losePathFocus(fixture)
      Disposer.dispose(fixture.panel)
      assertTrue("Disposing Settings should cancel the active worker", fixture.resolver.indicators.get("b.conf").isCanceled)
      fixture.resolver.load("b.conf")
      flushUi()
      assertEquals("A late preview must not touch the disposed editor", "", fixture.panel.getEditor.getDocument.getText)
    })
  }

  def testCancelledLookupCanRetryWithoutOfferingCreate(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.resolver.holdLoads = true
      fixture.pathField.setText("b.conf")
      losePathFocus(fixture)
      fixture.resolver.indicators.get("b.conf").cancel()
      flushUi()

      assertTrue("A cancelled lookup should offer a retry action",
        isVisibleInPanel(fixture.createButton, fixture.panel.getPanel))
      assertEquals("Cancellation must offer Retry rather than claim that the file is missing",
        ScalaSettingsBundle.message("scalafmt.settings.panel.retry"), fixture.createButton.getText)
      fixture.resolver.load("b.conf")
      flushUi()
      assertEquals("A late preview from a cancelled request must be ignored", "", fixture.panel.getEditor.getDocument.getText)

      fixture.createButton.doClick(0)
      assertEquals("Retry must not attempt to create a configuration file", 0, fixture.resolver.creationCount.get())
      fixture.resolver.load("b.conf")
      flushUi()
      assertEquals("Retry must load the selected configuration", "version = 3.11.5", fixture.panel.getEditor.getDocument.getText)
      assertFalse("A successful retry should hide the status action",
        isVisibleInPanel(fixture.createButton, fixture.panel.getPanel))
    })
  }

  def testCancellingResolutionPreservesPreviewEditsAndRejectsLateCompletion(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      inWriteAction(fixture.panel.getEditor.getDocument.setText("edits made during download"))
      fixture.resolver.indicators.get("a.conf").cancel()
      flushUi()
      assertEquals("Cancelling a download must preserve the loaded preview and its edits",
        "edits made during download", fixture.panel.getEditor.getDocument.getText)
      assertTrue("Unapplied preview edits must remain modified after cancellation", fixture.panel.isModified(fixture.settings))
      assertTrue("Cancelling a download must keep the loaded preview visible",
        isVisibleInPanel(fixture.panel.getEditor.getComponent, fixture.panel.getPanel))
    })
    fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
    assertVersion(fixture, "")
  }

  def testFailedLookupCanRetryWithoutOfferingCreate(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.resolver.holdLoads = true
      fixture.pathField.setText("b.conf")
      losePathFocus(fixture)
      fixture.resolver.fail("b.conf", new IllegalStateException("Synthetic loading failure"))
      flushUi()

      assertTrue("A failed lookup must offer recovery without claiming the file is missing",
        isVisibleInPanel(fixture.createButton, fixture.panel.getPanel))
      assertEquals("A failed lookup must offer Retry", ScalaSettingsBundle.message("scalafmt.settings.panel.retry"), fixture.createButton.getText)
      fixture.createButton.doClick(0)
      assertEquals("Retry after failure must not create a configuration file", 0, fixture.resolver.creationCount.get())
      fixture.resolver.load("b.conf")
      flushUi()
      assertEquals("Retry must replace failure feedback with the loaded preview",
        "version = 3.11.5", fixture.panel.getEditor.getDocument.getText)
    })
  }

  private def createFixture(formatterEnabled: Boolean = true, bindProject: Boolean = true): Fixture = EdtTestUtil.runInEdtAndGet(() => {
    // Relative paths need a physical content root discoverable by the panel's project-directory lookup.
    val projectRoot = createTestProjectStructure()
    for ((name, version) <- Seq("a.conf" -> "2.7.5", "b.conf" -> "3.11.5")) {
      val path = Path.of(projectRoot.getPath).resolve(name)
      Files.writeString(path, s"version = $version")
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    }

    // Unlike new CodeStyleSettings(), this factory avoids registering temporary settings globally.
    val settings = CodeStyle.createTestSettings()
    val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    scalaSettings.FORMATTER =
      if (formatterEnabled) ScalaCodeStyleSettings.SCALAFMT_FORMATTER else ScalaCodeStyleSettings.INTELLIJ_FORMATTER
    scalaSettings.SCALAFMT_CONFIG_PATH = "a.conf"
    val resolver = new ControlledResolver
    val panel = new ScalaFmtSettingsPanel(settings, resolver)
    Disposer.register(getTestRootDisposable, panel)
    val component = panel.getPanel
    if (bindProject) panel.onProjectSet(getProject) else panel.resetImpl(settings)

    val captions = UIUtil.findComponentsOfType(component, classOf[JLabel]).asScala
      .filter(_.getText == ScalaSettingsBundle.message("scalafmt.settings.panel.scalafmt.version.label") + ":")
    assertEquals("The panel should expose one formatter version caption", 1, captions.size)
    val label = captions.head.getLabelFor
    assertTrue("The version caption must identify its value label", label.isInstanceOf[JLabel])
    val pathFields = UIUtil.findComponentsOfType(component, classOf[TextFieldWithBrowseButton]).asScala
    assertEquals("The panel should expose one configuration path field", 1, pathFields.size)
    val createButtons = UIUtil.findComponentsOfType(component, classOf[JButton]).asScala
      .filter(_.getText == ScalaFormattingBundle.message("scalafmt.can.not.find.config.file.create.new"))
    assertEquals("The panel should expose one Create configuration action", 1, createButtons.size)
    flushUi()
    Fixture(panel, settings, label.asInstanceOf[JLabel], pathFields.head, createButtons.head, projectRoot, resolver)
  })

  private def losePathFocus(fixture: Fixture): Unit = {
    val textField = fixture.pathField.getTextField
    val event = new FocusEvent(textField, FocusEvent.FOCUS_LOST)
    // The panel has no window; deliver one focus event through the listeners installed on the actual Swing field.
    textField.getFocusListeners.foreach(_.focusLost(event))
    flushUi()
  }

  private def assertMissingConfiguration(fixture: Fixture): Unit = {
    assertEquals("A missing configuration must clear the previous preview text", "", fixture.panel.getEditor.getDocument.getText)
    assertFalse("A missing configuration must hide the preview",
      isVisibleInPanel(fixture.panel.getEditor.getComponent, fixture.panel.getPanel))
    assertTrue("A missing configuration must show the Create action",
      isVisibleInPanel(fixture.createButton, fixture.panel.getPanel))
  }

  private def isVisibleInPanel(component: Component, panel: Component): Boolean =
    component != null && component.isVisible &&
      ((component eq panel) || isVisibleInPanel(component.getParent, panel))

  private def assertVersion(fixture: Fixture, expected: String): Unit =
    EdtTestUtil.runInEdtAndWait(() => {
      // Flush the IDE invocation queue too, so ignored completions cannot pass merely by remaining queued.
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      assertEquals("The panel should display the version of its current configuration", expected, fixture.versionLabel.getText)
    }, false)

  private case class Fixture(
    panel: ScalaFmtSettingsPanel,
    settings: CodeStyleSettings,
    versionLabel: JLabel,
    pathField: TextFieldWithBrowseButton,
    createButton: JButton,
    projectRoot: VirtualFile,
    resolver: ControlledResolver
  )

  private def flushUi(): Unit = PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

  // Control the two pipeline callbacks; panel lifecycle, files, VFS and documents remain real.
  // Deliberately deliver even cancelled requests to exercise the panel's independent stale-result guard.
  private class ControlledResolver extends ScalafmtSettingsConfigResolver {
    val requestCount = new AtomicInteger()
    val creationCount = new AtomicInteger()
    var holdLoads = false
    private val callbacks = new ConcurrentHashMap[String, Completion => Unit]()
    private val loads = new ConcurrentHashMap[String, () => Unit]()
    val indicators = new ConcurrentHashMap[String, ProgressIndicator]()

    override def resolveConfigAsync(
      project: Project,
      configPath: String,
      onLoaded: Option[LoadedConfig] => Unit,
      onFinished: Completion => Unit,
      createConfig: Boolean
    ): ProgressIndicator = {
      requestCount.incrementAndGet()
      if (createConfig) creationCount.incrementAndGet()
      callbacks.put(configPath, onFinished)
      val indicator = new ProgressIndicatorBase
      new ProgressIndicatorListener {
        override def cancelled(): Unit = onFinished(Completion.Cancelled)
      }.installToProgress(indicator)
      indicators.put(configPath, indicator)
      loads.put(configPath, () => {
        val config = ScalafmtConfigUtils.projectConfigFile(project, configPath).flatMap { file =>
          inReadAction {
            Option(FileDocumentManager.getInstance().getDocument(file)).map { document =>
              LoadedConfig(file, document, document.getText)
            }
          }
        }
        onLoaded(config)
        if (config.isEmpty) onFinished(Completion.Resolved(Right(Version(DefaultVersion, isDefault = true))))
      })
      if (!holdLoads) load(configPath)
      indicator
    }

    def load(path: String): Unit = {
      val callback = loads.remove(path)
      assertNotNull(s"The panel should have requested loading of $path", callback)
      callback()
    }

    def complete(fileName: String, version: ScalafmtVersion): Unit = {
      val callback = callbacks.remove(fileName)
      assertNotNull(s"The panel should have requested resolution of $fileName", callback)
      callback(Completion.Resolved(Right(Version(version, isDefault = false))))
    }

    def fail(fileName: String, cause: Throwable): Unit = {
      val callback = callbacks.remove(fileName)
      assertNotNull(s"The panel should have requested resolution of $fileName", callback)
      callback(Completion.Failed(cause))
    }
  }
}
