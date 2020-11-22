package org.jetbrains.plugins.scala.compilationCharts.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.{JBPanel, JBScrollPane}
import com.intellij.util.ui.JBUI
import javax.swing.{JViewport, ScrollPaneConstants}
import net.miginfocom.swing.MigLayout

class CompilationChartsComponent(project: Project)
  extends JBPanel(new MigLayout("gap rel 0, ins 0")) {

  private val diagramsComponent = new DiagramsComponent(this, project, ActionPanel.defaultZoom)
  private val diagramsScrollPane = new JBScrollPane(diagramsComponent)
  diagramsComponent.scrollComponent = diagramsScrollPane

  locally {
    diagramsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)
    diagramsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)
    diagramsScrollPane.setBorder(JBUI.Borders.empty)
    diagramsScrollPane.getViewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE)
    diagramsScrollPane.setName("compilation-charts-scroll-pane") // for easier debugging

    val actionPanel = new ActionPanel(diagramsComponent.setZoom)
    add(actionPanel, "al right, wrap")
    add(diagramsScrollPane, "grow, push, span")
  }

  def updateData(): Unit = {
    diagramsComponent.updateData()
  }
}
