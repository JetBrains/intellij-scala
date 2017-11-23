package org.jetbrains.sbt.shell

import java.awt.event.{InputEvent, KeyEvent}
import javax.swing.KeyStroke

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.wm._
import com.intellij.openapi.wm.impl.ToolWindowImpl
import org.jetbrains.plugins.scala.icons.Icons

/**
  * Creates the sbt shell toolwindow, which is docked at the bottom of sbt projects.
  *
  * This factory is registered in SBT.xml
  */
class SbtShellToolWindowFactory extends ToolWindowFactory with DumbAware {

  override def init(toolWindow: ToolWindow): Unit = {
    toolWindow.setStripeTitle(SbtShellToolWindowFactory.title)
    toolWindow.setIcon(Icons.SBT_SHELL_TOOLWINDOW)

    val toolwindowId = toolWindow.asInstanceOf[ToolWindowImpl].getId
    val actionId = ActivateToolWindowAction.getActionIdForToolWindow(toolwindowId)
    addShortcuts(actionId)
  }

  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    val pm = SbtProcessManager.forProject(project)
    Future(pm.acquireShellRunner)
      .foreach(_.openShell(false))
  }

  // don't auto-activate because starting sbt shell is super heavy weight
  override def isDoNotActivateOnStart: Boolean = true

  private def addShortcuts(actionId: String): Unit = {
    val keymapManager = KeymapManager.getInstance()

    val defaultKeymap = keymapManager.getKeymap("$default")
    val defaultShortcut =
      new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK), null)
    defaultKeymap.addShortcut(actionId, defaultShortcut)

    // NetBeans SaveAll is the only conflicting shortcut, and has the alternative ctrl+s
    // so I think it's low impact to just remove this one conflict
    val netbeansKeymap = keymapManager.getKeymap("NetBeans 6.5")
    netbeansKeymap.removeShortcut("SaveAll", defaultShortcut)
  }
}

object SbtShellToolWindowFactory {
  val title = "sbt shell"
  val ID = "sbt-shell-toolwindow"
}
