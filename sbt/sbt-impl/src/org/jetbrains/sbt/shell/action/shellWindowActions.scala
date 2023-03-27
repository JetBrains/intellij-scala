package org.jetbrains.sbt.shell.action

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.RemoteDebugProcessHandler
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.remote.{RemoteConfiguration, RemoteConfigurationType}
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.ui.{ConsoleView, RunContentDescriptor, RunContentManager}
import com.intellij.execution.{ProgramRunnerUtil, RunManager, RunnerAndConfigurationSettings}
import com.intellij.find.{EditorSearchSession, FindManager, FindModel, FindUtil}
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.{Editor, SelectionModel}
import com.intellij.openapi.project.{DumbAwareAction, Project}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, executeOnPooledThread}
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.shell.action.SbtShellActionUtil.shellAlive
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication, SbtShellConsoleView, SbtShellToolWindowFactory}

import java.awt.event.{InputEvent, KeyEvent}
import javax.swing.KeyStroke
import scala.jdk.CollectionConverters._

final class SbtShellScrollToTheEndToolbarAction(editor: Editor) extends ScrollToTheEndToolbarAction(editor) {

  private val end = KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.CTRL_DOWN_MASK)
  private val shortcuts = new CustomShortcutSet(end)
  setShortcutSet(shortcuts)
}

/**
  * Starts or restarts sbt shell depending on running state.
  */
final class StartAction(project: Project) extends DumbAwareAction {
  copyShortcutFrom(ActionManager.getInstance.getAction(IdeActions.ACTION_DEFAULT_RUNNER))
  private val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.Execute)
  templatePresentation.setText(SbtBundle.message("sbt.shell.start"))

  override def actionPerformed(e: AnActionEvent): Unit =
    SbtShellToolWindowFactory.instance(project).foreach { toolWindow =>
      toolWindow.getContentManager.removeAllContents(true)

      executeOnPooledThread {
        SbtProcessManager.forProject(e.getProject).restartProcess()
      }
    }

  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    if (shellAlive(project)) {
      presentation.setIcon(AllIcons.Actions.Restart)
      presentation.setText(SbtBundle.message("sbt.shell.restart"))
    } else {
      presentation.setIcon(AllIcons.Actions.Execute)
      presentation.setText(SbtBundle.message("sbt.shell.start"))
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}

final class StopAction(project: Project) extends DumbAwareAction {
  copyFrom(ActionManager.getInstance.getAction(IdeActions.ACTION_STOP_PROGRAM))
  private val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.Suspend)
  templatePresentation.setText(SbtBundle.message("sbt.shell.stop"))
  templatePresentation.setDescription(null: String)

  override def actionPerformed(e: AnActionEvent): Unit =
    if (isEnabled) {
      executeOnPooledThread {
        SbtProcessManager.forProject(e.getProject).destroyProcess()
      }
    }

  override def update(e: AnActionEvent): Unit =
    e.getPresentation.setEnabled(isEnabled)

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  private def isEnabled: Boolean = shellAlive(project)
}

final class EOFAction(project: Project) extends DumbAwareAction {

  private val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.TraceOver) // TODO sensible icon
  templatePresentation.setText(SbtBundle.message("sbt.shell.ctrl.d.eof"))
  //noinspection ScalaExtractStringToBundle
  templatePresentation.setDescription("")

  private val ctrlD = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
  private val shortcuts = new CustomShortcutSet(ctrlD)
  setShortcutSet(shortcuts)

  override def actionPerformed(e: AnActionEvent): Unit = {
    SbtShellCommunication.forProject(project).send("\u0004")
  }
}

final class CopyFromHistoryViewerAction(view: SbtShellConsoleView) extends DumbAwareAction {

  setShortcutSet(CommonShortcuts.getCopy)

  override def update(e: AnActionEvent): Unit =
    e.getPresentation.setEnabled(CopyFromHistoryViewerAction.isEnabled(e, view))

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def actionPerformed(e: AnActionEvent): Unit =
    CopyFromHistoryViewerAction.copyFromHistoryToClipboard(e, view)
}

// copied from ConsoleViewImpl#ClearThisConsoleAction to avoid being replaced by Grep Console plugin
final class ClearThisConsoleAction(myConsoleView: ConsoleView) extends ClearConsoleAction {
  override def update(e: AnActionEvent): Unit = {
    val enabled = myConsoleView.getContentSize > 0
    e.getPresentation.setEnabled(enabled)
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    myConsoleView.clear()
  }
}

private object CopyFromHistoryViewerAction {
  def selectionModel(e: AnActionEvent, view: SbtShellConsoleView): SelectionModel = {
    //we have two editors in sbt-shell: console and history viewer
    //we try to use selection from the focused editor, if we have any
    val editorInFocus = e.getDataContext.getData(CommonDataKeys.EDITOR)
    val editor = if (editorInFocus != null) editorInFocus else view.getHistoryViewer
    editor.getSelectionModel
  }

  def isEnabled(e: AnActionEvent, view: SbtShellConsoleView): Boolean =
    selectionModel(e, view).hasSelection

  private def copyFromHistoryToClipboard(e: AnActionEvent, view: SbtShellConsoleView): Unit =
    selectionModel(e, view).copySelectionToClipboard()
}

final class FindAction(view: SbtShellConsoleView) extends DumbAwareAction {
  setShortcutSet(CommonShortcuts.getFind)

  /**
   * The implementation was inspired by original "Find" action logic from
   * [[com.intellij.openapi.editor.actions.IncrementalFindAction.Handler]]
   */
  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = view.getProject
    val historyViewer = view.getHistoryViewer

    val editorInContext = e.getDataContext.getData(CommonDataKeys.EDITOR)
    val editorInFocus = if (editorInContext != null) editorInContext else view.getHistoryViewer

    //we don't need "replace" feature for history viewer because it's not modifiable
    //note: it's still possible to replace the content when you invoke "replace" in "find" widget, due to this bug: IDEA-302629
    val replace = false

    val search = EditorSearchSession.get(historyViewer)
    if (search != null) {
      val model = search.getFindModel
      FindUtil.configureFindModel(replace, editorInFocus, model, false)
      search.getComponent.requestFocusInTheSearchFieldAndSelectContent(project)
    }
    else {
      val findManager = FindManager.getInstance(project)
      val model = new FindModel
      model.copyFrom(findManager.getFindInFileModel)

      val search = EditorSearchSession.start(historyViewer, model, project)

      FindUtil.configureFindModel(replace, editorInFocus, model, true)
      search.getComponent.requestFocusInTheSearchFieldAndSelectContent(project)
    }
  }
}

//needed to be able to close search panel
final class EscapeAction(view: SbtShellConsoleView) extends DumbAwareAction {
  setShortcutSet(CommonShortcuts.ESCAPE)

  override def actionPerformed(e: AnActionEvent): Unit = {
    val searchSession = EditorSearchSession.get(view.getEditor)
    if (searchSession != null) {
      searchSession.close()
    }
  }
}

final class DebugShellAction(project: Project, remoteConnection: Option[RemoteConnection]) extends ToggleAction {
  copyShortcutFrom(ActionManager.getInstance.getAction(IdeActions.ACTION_DEFAULT_DEBUGGER))
  private val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.StartDebugger)
  if (remoteConnection.isDefined) {
    templatePresentation.setText(SbtBundle.message("sbt.shell.attach.debugger"))
    templatePresentation.setEnabled(false)
  } else {
    templatePresentation.setText(SbtBundle.message("sbt.shell.enable.debugging.in.sbt.settings"))
  }

  private val configType = new RemoteConfigurationType
  private val configName = SbtBundle.message("sbt.shell.debug")

  override def setSelected(e: AnActionEvent, toSet: Boolean): Unit = {
    val active = isSelected(e)
    if (toSet && !active) attach()
    else if (!toSet && active) detach()
  }

  override def isSelected(e: AnActionEvent): Boolean = {
    findSession.fold(false) { session => session.isAttached || session.isConnecting }
  }

  private def attach(): Unit = remoteConnection.foreach { remote =>

    val runManager = RunManager.getInstance(project)

    val runConfig =
      findRunConfig
        .getOrElse {
          val rc = runManager.createConfiguration(configName, configType)
          rc.setTemporary(true)
          rc.setActivateToolWindowBeforeRun(false)
          rc.getConfiguration.setAllowRunningInParallel(false)
          runManager.setTemporaryConfiguration(rc)
          rc
        }

    val settings = runConfig.getConfiguration.asInstanceOf[RemoteConfiguration]
    settings.PORT = remote.getDebuggerAddress
    settings.HOST = remote.getDebuggerHostName
    settings.SERVER_MODE = remote.isServerMode
    settings.USE_SOCKET_TRANSPORT = remote.isUseSockets

    val environmentBuilder = ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance, runConfig)
    ProgramRunnerUtil.executeConfiguration(environmentBuilder.build(), false, false)
  }

  private def detach(): Unit = {
    val descriptors = RunContentManager.getInstance(project).getAllDescriptors

    // This is pretty hacky, is there a cleaner way to handle the detaching?
    for {
      proc <- findProcessHandlerByNameAndType(descriptors.asScala)
    } ExecutionManagerImpl.stopProcess(proc)
  }

  private def findProcessHandlerByNameAndType(descriptors: Iterable[RunContentDescriptor]): Option[ProcessHandler] =
    for {
      desc <- descriptors.find(_.getDisplayName == configName)
      proc <- Option(desc.getProcessHandler)
      if proc.is[RemoteDebugProcessHandler]
      if !(proc.isProcessTerminated || proc.isProcessTerminating)
    } yield proc

  private def findRunConfig: Option[RunnerAndConfigurationSettings] = {
    val runManager = RunManager.getInstance(project)
    Option(runManager.findConfigurationByTypeAndName(configType.getId, configName))
  }

  private def findSession: Option[DebuggerSession] = {
    val sessions = DebuggerManagerEx.getInstanceEx(project).getSessions
    sessions.asScala.find(_.getSessionName == configName)
  }

  override def update(e: AnActionEvent): Unit =
    e.getPresentation.setEnabled(isEnabled)

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  private def isEnabled: Boolean = remoteConnection.isDefined && shellAlive(project)
}

private object SbtShellActionUtil {
  def shellAlive(project: Project): Boolean = SbtProcessManager.forProject(project).isAlive
}
