package org.jetbrains.sbt.shell.optionsWarn

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.plugins.scala.build.{BuildMessages, BuildToolWindowReporter}
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.process.options.reporting.{SbtOptionsDiagnosticsReporter, SbtOptionsWarningData}

import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import scala.concurrent.Promise

/**
 * Shows a short warning banner for sbt option diagnostics above the sbt shell output.
 *
 * The regular [[com.intellij.ui.EditorNotificationProvider]] API cannot be used here because it is driven by
 * [[com.intellij.openapi.fileEditor.FileEditorManager]] and is invoked only for file editors backed by
 * [[com.intellij.openapi.vfs.VirtualFile]].
 * The sbt shell history is an editor inside [[com.intellij.execution.console.LanguageConsoleImpl]],
 * not a file editor, so the service attaches the banner directly to the history viewer via [[EditorEx.setPermanentHeaderComponent]].
 * For the terminal-based sbt shell, the service uses [[TerminalWidget.addNotification]].
 *
 * IMPLEMENTATION NOTE:<br>
 * Warning data can arrive before the shell UI is created. For that reason the service stores the latest warning state
 * independently of the currently installed UI target and refreshes the banner when the target is later installed.
 * Closing or hiding the banner clears only this stored warning state.
 *
 * The banner intentionally contains only a compact summary. Full rendered diagnostics are shown on demand in a
 * dedicated sbt shell startup view in the Build tool window, so long option lists do not pollute or scroll away in the
 * shell output.
 */
@Service(Array(Service.Level.PROJECT))
private[shell] final class SbtShellOptionsWarningService(project: Project) {

  import SbtShellOptionsWarningService.*

  private val warningState = new AtomicReference[Option[OptionsWarning]](None)

  @volatile private var warningsHost: Option[SbtShellWarningsHost] = None

  def installHistoryViewer(editor: EditorEx): Unit =
    installHost(new EditorHeaderWarningsHost(editor))

  def uninstallHistoryViewer(editor: EditorEx): Unit =
    uninstallHost(_.isForHistoryViewer(editor))

  def installTerminalWidget(widget: TerminalWidget): Unit =
    installHost(new TerminalWidgetWarningsHost(project, widget))

  def uninstallTerminalWidget(widget: TerminalWidget): Unit =
    uninstallHost(_.isForTerminalWidget(widget))

  private def installHost(host: SbtShellWarningsHost): Unit =
    invokeLater {
      if (!project.isDisposed) {
        // The legacy and terminal shells are mutually exclusive for one running sbt process.
        warningsHost.foreach(_.clear())
        warningsHost = Some(host)
        refresh()
      }
    }

  private def uninstallHost(matches: SbtShellWarningsHost => Boolean): Unit =
    invokeLater {
      if (!project.isDisposed) {
        warningsHost.filter(matches).foreach { host =>
          host.clear()
          warningsHost = None
        }
      }
    }

  def showWarnings(warnings: Seq[SbtOptionsWarningData]): Unit =
    if (warnings.isEmpty) {
      hide()
    } else {
      // Keep the shell startup notice attached to the shell instead of printing it to the process output:
      // startup output can scroll away quickly. Detailed diagnostics are shown on demand in the Build tool window,
      // where the regular sbt option reporting UI has enough room for all warnings.
      warningState.set(Some(OptionsWarning(warnings)))
      refreshLater()
    }

  private def hide(): Unit = {
    warningState.set(None)
    refreshLater()
  }

  private def refreshLater(): Unit =
    invokeLater {
      if (!project.isDisposed) {
        refresh()
      }
    }

  private def refresh(): Unit =
    warningsHost.foreach(_.update(warningState.get().map(createPanel)))

  private def createPanel(warning: OptionsWarning): EditorNotificationPanel = {
    val panel = new EditorNotificationPanel(EditorNotificationPanel.Status.Warning)
    panel.setText(NotificationText)
    panel.setCloseAction(() => hide())
    panel.createActionLabel(
      SbtBundle.message("sbt.options.shell.notification.show.details"),
      (() => showDetails(warning)): Runnable,
    )
    panel.createActionLabel(SbtBundle.message("sbt.options.shell.notification.hide"), (() => hide()): Runnable)
    panel
  }

  private def showDetails(warning: OptionsWarning): Unit = {
    val buildId = BuildMessages.randomEventId
    val resultPromise = Promise[BuildMessages]()
    val buildReporter = new BuildToolWindowReporter(
      project,
      buildId,
      SbtBundle.message("sbt.options.shell.startup.title"),
      SbtShellBuildViewManager.instance(project),
      new BuildToolWindowReporter.CancelBuildAction(resultPromise, indicator = None),
      activateToolWindowWhenFailed = false,
      activateToolWindowWhenWarned = true,
    )
    val result = BuildMessages.empty.status(BuildMessages.OK)

    buildReporter.start()

    SbtOptionsDiagnosticsReporter.reportWarnings(buildReporter, warning.warnings)

    resultPromise.success(result)
    buildReporter.finish(result)
  }
}

private[shell] object SbtShellOptionsWarningService {
  private final case class OptionsWarning(warnings: Seq[SbtOptionsWarningData])

  private trait SbtShellWarningsHost {
    def update(component: Option[JComponent]): Unit
    def clear(): Unit = update(None)

    def isForHistoryViewer(editor: EditorEx): Boolean = false
    def isForTerminalWidget(widget: TerminalWidget): Boolean = false
  }

  // Legacy sbt shell: LanguageConsoleImpl exposes the console output as an EditorEx history viewer.
  private final class EditorHeaderWarningsHost(editor: EditorEx) extends SbtShellWarningsHost {
    override def update(component: Option[JComponent]): Unit =
      if (!editor.isDisposed) {
        val hasHeader = editor.hasHeaderComponent
        editor.setPermanentHeaderComponent(component.orNull)
        if (!hasHeader) {
          editor.setHeaderComponent(null)
        }
      }

    override def isForHistoryViewer(other: EditorEx): Boolean =
      editor == other
  }

  // New sbt shell: TerminalExecutionConsole exposes a TerminalWidget, not an EditorEx history viewer.
  private final class TerminalWidgetWarningsHost(project: Project, widget: TerminalWidget) extends SbtShellWarningsHost {
    private var notificationDisposable: Option[Disposable] = None

    override def update(component: Option[JComponent]): Unit = {
      clear()
      component.foreach { notificationComponent =>
        val disposable = Disposer.newDisposable("sbt shell options warning notification")
        notificationDisposable = Some(disposable)
        Disposer.register(project, disposable)
        widget.addNotification(notificationComponent, disposable)
      }
    }

    override def clear(): Unit = {
      notificationDisposable.foreach(Disposer.dispose)
      notificationDisposable = None
    }

    override def isForTerminalWidget(other: TerminalWidget): Boolean =
      widget == other
  }

  private val NotificationText: String =
    SbtBundle.message("sbt.options.shell.notification.text")

  def instance(project: Project): SbtShellOptionsWarningService =
    project.getService(classOf[SbtShellOptionsWarningService])
}
