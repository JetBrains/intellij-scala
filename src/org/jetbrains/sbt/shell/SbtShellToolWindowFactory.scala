package org.jetbrains.sbt.shell

import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.wm.{ToolWindow, ToolWindowAnchor, ToolWindowFactory, ToolWindowType}
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
    toolWindow.setAnchor(ToolWindowAnchor.BOTTOM, null)
    toolWindow.setType(ToolWindowType.DOCKED, null)
  }

  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    val pm = SbtProcessManager.forProject(project)
    pm.openShellRunner()
  }

  // TODO should probably only be available for SBT projects
  override def shouldBeAvailable(project: Project): Boolean = true

  // don't auto-activate because starting sbt shell is super heavy weight
  override def isDoNotActivateOnStart: Boolean = true

}

object SbtShellToolWindowFactory {
  val title = "SBT Shell"
  val ID = "sbt-shell-toolwindow"
}
