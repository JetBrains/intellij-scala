package org.jetbrains.plugins.scala.worksheet.ui

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.{ConsoleView, RunnerLayoutUi}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.wm.{ToolWindow, ToolWindowAnchor, ToolWindowFactory, ToolWindowManager}
import com.intellij.ui.content.Content
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * User: Dmitry.Naydanov
  * Date: 25.05.18.
  */
class WorksheetToolWindowFactory extends ToolWindowFactory with DumbAware  {
  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    
  }

  override def init(window: ToolWindow): Unit = {
    WorksheetToolWindowFactory.initToolWindow(window)
  }

  override def shouldBeAvailable(project: Project): Boolean = false

  override def isDoNotActivateOnStart: Boolean = true
}

object WorksheetToolWindowFactory {
  private val WORKSHEET_NAME = "Worksheet"
  private val MY_ID = "WorksheetResultsToolWindow"
  
  private def findToolWindow(project: Project)= {
    Option(ToolWindowManager.getInstance(project).getToolWindow(MY_ID)) //TODO awful solution 
  }
  
  private def initToolWindow(toolWindow: ToolWindow): ToolWindow = {
    toolWindow.setIcon(Icons.WORKSHEET_LOGO)
    toolWindow.setTitle("Worksheet Output")
    toolWindow.setStripeTitle("Worksheet")
    toolWindow
  }
  
  private def createOutputContent(file: ScalaFile): (Content, ConsoleView) = {
    val project = file.getProject
    val factory = RunnerLayoutUi.Factory.getInstance(project)
    val layoutUi = factory.create("WorksheetConsolePrinter", WORKSHEET_NAME, WORKSHEET_NAME, project)
    
    
    val cv = new ConsoleViewImpl(project, false)
    val component = cv.getComponent //don't inline, it initializes cv's editor
    
    (layoutUi.createContent(MY_ID, component, getDisplayName(file), Icons.WORKSHEET_LOGO, null), cv)
  }
  
  
  //todo
  private def enableToolWindow(project: Project): Unit = {
    val manager = ToolWindowManager.getInstance(project)
    
    (manager.getToolWindow(MY_ID) match {
      case tw: ToolWindow => tw
      case _ => initToolWindow(manager.registerToolWindow(MY_ID, false, ToolWindowAnchor.BOTTOM))
    }).activate(null)
  }

  private def disableToolWindow(project: Project): Unit = {
    ToolWindowManager.getInstance(project).getToolWindow(MY_ID).hide(null)
    ToolWindowManager.getInstance(project).unregisterToolWindow(MY_ID)
  }
  
  private def ensureRunning(project: Project, isRunning: Boolean) {
    if (isRunning^findToolWindow(project).exists(_.isActive)) {
      if (isRunning) enableToolWindow(project) else disableToolWindow(project)
    }
  }

  private def getDisplayName(file: ScalaFile): String = file.getName
  
  def disposeUI(content: Content, project: Project): Unit = {
    findToolWindow(project) foreach {
      toolWindow =>
        val contentManager = toolWindow.getContentManager
        contentManager.removeContent(content, true)
        if (contentManager.getContents.isEmpty) disableToolWindow(project)
    }
  }

  //todo Can we really get all content<->file managing to factory and cache? 
  def createOutputContent(file: ScalaFile, force: Boolean = true): Option[(Content, ConsoleView)] = {
    val project = file.getProject
    if (force) ensureRunning(project, isRunning = true)

    findToolWindow(project).map {
      toolWindow =>
        val manager = toolWindow.getContentManager
        val (content, cv) = createOutputContent(file)
        manager addContent content

        (content, cv)
    }
  }
}