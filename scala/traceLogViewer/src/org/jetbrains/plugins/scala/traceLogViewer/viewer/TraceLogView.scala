package org.jetbrains.plugins.scala.traceLogViewer.viewer

import com.intellij.openapi.actionSystem.{ActionManager, DefaultActionGroup}
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.wm.{ToolWindow, ToolWindowManager}
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.{Content, ContentFactory}
import com.intellij.ui.dualView.TreeTableView
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.traceLogViewer.{ClickableColumn, TraceLogViewerWindowFactory}

import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JPanel
import scala.io.Source

object TraceLogView {
  private val ActionToolbarPlace = "scala-trace-log-view-actionbar"

  def openTraceLog(path: Path): Unit = {
    ProjectManager.getInstance()
      .getOpenProjects
      .headOption
      .foreach(openTraceLog(path, _))
  }

  def openTraceLog(path: Path, project: Project): Unit = invokeLater {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(TraceLogViewerWindowFactory.Id)
    openTraceLog(path, toolWindow)
  }

  def openTraceLog(path: Path, toolWindow: ToolWindow): Unit = {
    val contentManager = toolWindow.getContentManager
    val content = createContentFromFile(path)
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)
  }

  def createContentFromFile(path: Path): Content = {
    val source = Source.fromFile(path.toFile)
    val model =
      try TraceLogModel.createFromLines(source.getLines())
      finally source.close()
    createContent(path, model)
  }

  def createContent(path: Path, model: TraceLogModel): Content = {
    val actionToolbarPanel = new JPanel
    val actionGroup = new DefaultActionGroup(
    )
    val actionToolBar = ActionManager.getInstance().createActionToolbar(ActionToolbarPlace, actionGroup, false)
    actionToolbarPanel.setLayout(new BorderLayout)
    actionToolbarPanel.add(actionToolBar.getComponent)

    val table = new TreeTableView(model)
    model.registerSpeedSearch(table)
    ClickableColumn.install(table)

    val scrollPane = new JBScrollPane
    scrollPane.setViewportView(table)

    val mainPanel = new JPanel()
    mainPanel.setLayout(new BorderLayout)
    mainPanel.add(actionToolbarPanel, BorderLayout.WEST)
    mainPanel.add(scrollPane, BorderLayout.CENTER)

    actionToolBar.setTargetComponent(mainPanel)

    val factory = ContentFactory.SERVICE.getInstance()
    factory.createContent(mainPanel, NlsString.force(path.getFileName.toString), true)
  }
}
