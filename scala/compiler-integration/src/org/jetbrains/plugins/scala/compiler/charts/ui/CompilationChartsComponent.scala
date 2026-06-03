package org.jetbrains.plugins.scala.compiler.charts.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel

import javax.swing.{JViewport, ScrollPaneConstants}

class CompilationChartsComponent(project: Project)
  extends BorderLayoutPanel {

  private val diagramsComponent = new DiagramsComponent(this, project, ActionPanel.defaultZoom)
  private val diagramsScrollPane = new JBScrollPane(diagramsComponent)
  diagramsComponent.scrollComponent = diagramsScrollPane

  locally {
    diagramsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)
    diagramsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)
    diagramsScrollPane.setBorder(JBUI.Borders.empty)
    diagramsScrollPane.getViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE)
    diagramsScrollPane.setName("compilation-charts-scroll-pane") // for easier debugging

    val actionPanel = new ActionPanel(diagramsComponent.setZoom, diagramsComponent.setLevel)
    addToTop(actionPanel)
    addToCenter(diagramsScrollPane)
  }

  def updateData(): Unit =
    diagramsComponent.updateData()
}
