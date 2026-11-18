package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.{EdtTestUtil, HeavyPlatformTestCase, PlatformTestUtil}
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.settings.ScalaSettingsBundle
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicService.DefaultVersion
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalafmtSettingsConfigResolver.{Result, Version}
import org.junit.Assert._
import org.scalafmt.dynamic.ScalafmtVersion

import java.nio.file.{Files, Path}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JLabel
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

  def testChoosingDefaultInvalidatesPendingConfigurationResolution(): Unit = {
    val fixture = createFixture()
    EdtTestUtil.runInEdtAndWait(() => {
      fixture.settings.getCustomSettings(classOf[ScalaCodeStyleSettings]).SCALAFMT_CONFIG_PATH = "missing.conf"
      fixture.panel.resetImpl(fixture.settings)
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
    })

    fixture.resolver.complete("a.conf", ScalafmtVersion(2, 7, 5))
    assertVersion(fixture, "")
  }

  private def createFixture(): Fixture = EdtTestUtil.runInEdtAndGet(() => {
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
    scalaSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
    scalaSettings.SCALAFMT_CONFIG_PATH = "a.conf"
    val resolver = new ControlledResolver
    val panel = new ScalaFmtSettingsPanel(settings, resolver)
    Disposer.register(getTestRootDisposable, panel)
    val component = panel.getPanel
    panel.onProjectSet(getProject)

    val captions = UIUtil.findComponentsOfType(component, classOf[JLabel]).asScala
      .filter(_.getText == ScalaSettingsBundle.message("scalafmt.settings.panel.scalafmt.version.label") + ":")
    assertEquals("The panel should expose one formatter version caption", 1, captions.size)
    val label = captions.head.getLabelFor
    assertTrue("The version caption must identify its value label", label.isInstanceOf[JLabel])
    Fixture(panel, settings, label.asInstanceOf[JLabel], resolver)
  })

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
    resolver: ControlledResolver
  )

  // Only formatter resolution is controlled; panel lifecycle, files, VFS and documents remain real.
  private class ControlledResolver extends ScalafmtSettingsConfigResolver {
    private val callbacks = new ConcurrentHashMap[String, Result => Unit]()

    override def resolveConfigAsync(project: Project, configFile: VirtualFile, onResolved: Result => Unit): Unit = {
      callbacks.put(configFile.getName, onResolved)
    }

    override def resolveDefaultFormatterAsync(project: Project): Unit = ()

    def complete(fileName: String, version: ScalafmtVersion): Unit = {
      val callback = callbacks.remove(fileName)
      assertNotNull(s"The panel should have requested resolution of $fileName", callback)
      callback(Right(Version(version, isDefault = false)))
    }
  }
}
