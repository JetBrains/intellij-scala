package org.jetbrains.plugins.scala.traceLogViewer.selection

import com.intellij.openapi.actionSystem.{ActionManager, DefaultActionGroup}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.{Content, ContentFactory}
import com.intellij.ui.table.TableView
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.traceLogViewer.viewer.TraceLogView

import java.awt.BorderLayout
import java.awt.event.{HierarchyEvent, MouseAdapter, MouseEvent}
import javax.swing.RowSorter.SortKey
import javax.swing.{JPanel, SortOrder}
import scala.jdk.CollectionConverters._

object TraceLogSelectionView {
  private val ActionToolbarPlace = "scala-trace-log-selection-view-actionbar"

  @Nls
  val displayName = "Logs"

  def create(toolWindow: ToolWindow): Content = {
    val actionToolbarPanel = new JPanel
    val actionGroup = new DefaultActionGroup(
    )
    val actionToolBar = ActionManager.getInstance().createActionToolbar(ActionToolbarPlace, actionGroup, false)
    actionToolbarPanel.setLayout(new BorderLayout)
    actionToolbarPanel.add(actionToolBar.getComponent)

    val table = new TableView(TraceLogSelectionModel)
    table.addMouseListener(new MouseAdapter {
      override def mouseClicked(e: MouseEvent): Unit =
        if (e.getClickCount == 2) {
          Option(table.getSelectedObject)
            .foreach(entry => TraceLogView.openTraceLog(entry.path, toolWindow))
        }
    })
    table.getRowSorter.setSortKeys(Seq(new SortKey(1, SortOrder.DESCENDING)).asJava)

    val scrollPane = new JBScrollPane
    scrollPane.setViewportView(table)

    val mainPanel = new JPanel()
    mainPanel.setLayout(new BorderLayout)
    mainPanel.add(actionToolbarPanel, BorderLayout.WEST)
    mainPanel.add(scrollPane, BorderLayout.CENTER)

    mainPanel.addHierarchyListener((e: HierarchyEvent) => {
      if (HierarchyEvent.SHOWING_CHANGED == (e.getChangeFlags & HierarchyEvent.SHOWING_CHANGED))
        TraceLogSelectionModel.refresh()
    })

    val factory = ContentFactory.SERVICE.getInstance()
    factory.createContent(mainPanel, displayName, true)
  }

  def refresh(): Unit =
    ApplicationManager.getApplication.invokeLater(() => TraceLogSelectionModel.refresh())
}
