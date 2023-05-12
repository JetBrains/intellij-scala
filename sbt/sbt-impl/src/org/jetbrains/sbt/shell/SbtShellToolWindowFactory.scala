package org.jetbrains.sbt.shell

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.wm._
import com.intellij.openapi.wm.impl.ToolWindowImpl
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.{invokeLater, schedulePeriodicTask}
import org.jetbrains.sbt.icons.Icons
import org.jetbrains.sbt.shell.SbtShellToolWindowFactory.{Log, scheduleIconUpdate}
import org.jetbrains.sbt.{SbtBundle, SbtUtil, shell}

import java.awt.event.{InputEvent, KeyEvent}
import java.awt.{Component, Container, FocusTraversalPolicy}
import java.util.concurrent.TimeUnit
import javax.swing.{Icon, KeyStroke}

/**
  * Creates the sbt shell toolwindow, which is docked at the bottom of sbt projects.
  *
  * This factory is registered in SBT.xml
  */
class SbtShellToolWindowFactory extends ToolWindowFactory with DumbAware {

  /**
   * NOTE: when a new project is created this returns false<br>
   * In this case [[org.jetbrains.sbt.project.ShowSbtShellAfterCreatingNewProject]] explicitly activates the tool window
   */
  override def shouldBeAvailable(project: Project): Boolean =
    SbtUtil.isSbtProject(project)

  // called once per project open
  override def init(toolWindow: ToolWindow): Unit = {
    Log.debug("init")
    toolWindow.setStripeTitle(SbtShellToolWindowFactory.Title)
    toolWindow.setIcon(Icons.SBT_SHELL_TOOL_WINDOW)

    val toolWindowId = toolWindow.asInstanceOf[ToolWindowImpl].getId
    val actionId = ActivateToolWindowAction.getActionIdForToolWindow(toolWindowId)

    addShortcuts(actionId)
  }

  // called once per project open, is not called during sbt shell restart OR close/open etc...
  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    Log.debug("createToolWindowContent")
    // focus sbt shell input when opening toolWindow with shortcut. #SCL-13225
    val defaultFocusPolicy = toolWindow.getComponent.getFocusTraversalPolicy
    val focusPolicy = new shell.SbtShellToolWindowFactory.TraversalPolicy(project, defaultFocusPolicy)
    toolWindow.getComponent.setFocusTraversalPolicy(focusPolicy)

    scheduleIconUpdate(project, toolWindow)

    SbtProcessManager.forProject(project).initAndRunAsync()
  }

  private def addShortcuts(actionId: String): Unit = {
    val keymapManager = KeymapManager.getInstance()

    val defaultShortcut =
      new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK), null)

    val defaultKeymap = keymapManager.getKeymap("$default")
    if (defaultKeymap != null) {
      defaultKeymap.addShortcut(actionId, defaultShortcut)
    }

    // NetBeans SaveAll is the only conflicting shortcut, and has the alternative ctrl+s
    // so I think it's low impact to just remove this one conflict
    val netbeansKeymap = keymapManager.getKeymap("NetBeans 6.5")
    if (netbeansKeymap != null) {
      netbeansKeymap.removeShortcut("SaveAll", defaultShortcut)
    }
  }
}

object SbtShellToolWindowFactory {

  private val Log = Logger.getInstance(getClass)

  @Nls
  def Title: String = SbtBundle.message("sbt.shell.title")
  val ID = "sbt-shell-toolwindow"

  // TODO: we could pass ToolWindow directly to ProcessManager ans SbtShellRunner
  def instance(implicit project: Project): Option[ToolWindow] = {
    val result = for {
      manager <- Option(ToolWindowManager.getInstance(project))
      window <- Option(manager.getToolWindow(ID))
    } yield window

    if (result.isEmpty) {
      Log.error(s"Failed to create sbt shell toolwindow content for $project.")
    }

    result
  }

  private def currentIcon(project: Project): Icon = {
    val baseIcon = Icons.SBT_SHELL_TOOL_WINDOW

    def isAlive = SbtProcessManager.forProject(project).isAlive

    if (!project.isDisposed && isAlive) ExecutionUtil.getLiveIndicator(baseIcon)
    else baseIcon
  }

  private def scheduleIconUpdate(project: Project, toolWindow: ToolWindow): Unit =
    schedulePeriodicTask(500L, TimeUnit.MILLISECONDS, toolWindow.getContentManager) {
      invokeLater {
        if (!project.isDisposed) {
          toolWindow.setIcon(currentIcon(project))
        }
      }
    }

  private class TraversalPolicy(project: Project, defaultPolicy: FocusTraversalPolicy) extends FocusTraversalPolicy {
    override def getComponentAfter(aContainer: Container, aComponent: Component): Component =
      defaultPolicy.getComponentAfter(aContainer, aComponent)

    override def getComponentBefore(aContainer: Container, aComponent: Component): Component =
      defaultPolicy.getComponentBefore(aContainer, aComponent)

    override def getFirstComponent(aContainer: Container): Component =
      defaultPolicy.getFirstComponent(aContainer)

    override def getLastComponent(aContainer: Container): Component =
      defaultPolicy.getLastComponent(aContainer)

    override def getDefaultComponent(aContainer: Container): Component = {
      // default implementation focuses the toolwindow frame, but we want the editor to be directly focused to edit it directly
      val sbtManager = SbtProcessManager.forProject(project)
      val shellComponent = for {
        shellRunner <- sbtManager.shellRunner
        if sbtManager.isAlive
        view <- Option(shellRunner.getConsoleView)
        editor <- Option(view.getConsoleEditor)
      } yield editor.getContentComponent
      shellComponent.getOrElse(defaultPolicy.getDefaultComponent(aContainer))
    }
  }
}
