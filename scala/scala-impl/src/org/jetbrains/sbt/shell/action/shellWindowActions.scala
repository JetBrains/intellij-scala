package org.jetbrains.sbt.shell.action

import java.awt.event.{InputEvent, KeyEvent}
import javax.swing.{Icon, KeyStroke}

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.RemoteDebugProcessHandler
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.remote.{RemoteConfiguration, RemoteConfigurationType}
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.{ExecutionManager, ProgramRunnerUtil, RunManager}
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.project.{DumbAwareAction, Project}
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication, SbtShellToolWindowFactory}

import scala.collection.JavaConverters._

class RestartAction(project: Project) extends DumbAwareAction {

  val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.Restart)
  templatePresentation.setText("Restart SBT Shell") // TODO i18n / language-bundle

  def actionPerformed(e: AnActionEvent): Unit = {
    val twm = ToolWindowManager.getInstance(project)
    val toolWindow = twm.getToolWindow(SbtShellToolWindowFactory.ID)
    toolWindow.getContentManager.removeAllContents(true)

    SbtProcessManager.forProject(e.getProject).restartProcess()
  }
}

class StopAction(project: Project) extends DumbAwareAction {
  copyFrom(ActionManager.getInstance.getAction(IdeActions.ACTION_STOP_PROGRAM))
  val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Process.Stop)
  templatePresentation.setText("Stop SBT Shell") // TODO i18n / language-bundle
  templatePresentation.setDescription(null)

  override def actionPerformed(e: AnActionEvent): Unit = {
    SbtProcessManager.forProject(e.getProject).destroyProcess()
  }
}

class ExecuteTaskAction(task: String, icon: Option[Icon]) extends DumbAwareAction {

  getTemplatePresentation.setIcon(icon.orNull)
  getTemplatePresentation.setText(s"Execute $task")

  override def actionPerformed(e: AnActionEvent): Unit = {
    // TODO execute with indicator
    SbtShellCommunication.forProject(e.getProject).command(task)
  }
}

class EOFAction(project: Project) extends DumbAwareAction {

  private val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.TraceOver) // TODO sensible icon
  templatePresentation.setText("Ctrl+D EOF")
  templatePresentation.setDescription("")

  private val ctrlD = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
  private val shortcuts = new CustomShortcutSet(ctrlD)
  setShortcutSet(shortcuts)

  override def actionPerformed(e: AnActionEvent): Unit = {
    SbtShellCommunication.forProject(project).send("\u0004")
  }
}

class DebugShellAction(project: Project, remoteConnection: RemoteConnection) extends ToggleAction {

  private val templatePresentation: Presentation = getTemplatePresentation
  templatePresentation.setIcon(AllIcons.Actions.StartDebugger)
  templatePresentation.setText("Attach debugger to SBT Shell")

  private val configType = new RemoteConfigurationType
  private val configName = "Debug SBT Shell"
  private val configFactory = configType.getFactory

  override def setSelected(e: AnActionEvent, toSet: Boolean): Unit = {
    val active = isSelected(e)
    if (toSet && !active) attach()
    else if (!toSet && active) detach()
  }

  override def isSelected(e: AnActionEvent): Boolean = {
    findSession.fold(false) { session => session.isAttached || session.isConnecting }
  }

  private def attach(): Unit = {

    val runManager = RunManager.getInstance(project)

    val runConfig =
      findRunConfig
        .getOrElse {
          val rc = runManager.createConfiguration(configName, configFactory)
          runManager.setTemporaryConfiguration(rc)
          rc
        }

    val settings = runConfig.getConfiguration.asInstanceOf[RemoteConfiguration]
    settings.PORT = remoteConnection.getAddress
    settings.HOST = remoteConnection.getHostName
    settings.SERVER_MODE = remoteConnection.isServerMode
    settings.USE_SOCKET_TRANSPORT = remoteConnection.isUseSockets

    val environmentBuilder = ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance, runConfig)
    ProgramRunnerUtil.executeConfiguration(environmentBuilder.build(), false, false)

  }

  private def detach(): Unit = {
    val executionManager = ExecutionManager.getInstance(project)
    val descriptors = executionManager.getContentManager.getAllDescriptors

    // this finds the process handler by name and type. This is pretty hacky.
    // is there a cleaner way to handle the detaching?
    for {
      desc <- descriptors.asScala.find { d => d.getDisplayName == configName }
      proc <- Option(desc.getProcessHandler)
      if proc.isInstanceOf[RemoteDebugProcessHandler]
      if !(proc.isProcessTerminated || proc.isProcessTerminating)
    } ExecutionManagerImpl.stopProcess(proc)
  }

  private def findRunConfig = {
    val runManager = RunManager.getInstance(project)
    Option(runManager.findConfigurationByTypeAndName(configType.getId,  configName))
  }

  private def findSession = {
    val sessions = DebuggerManagerEx.getInstanceEx(project).getSessions
    sessions.asScala
      .find { session => session.getSessionName == configName }
  }

}
