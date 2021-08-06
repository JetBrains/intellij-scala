package org.jetbrains.plugins.scala.traceLogViewer.selection

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionManager, AnAction, AnActionEvent, DefaultActionGroup}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.{FileChooser, FileTypeDescriptor}
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.{Content, ContentFactory}
import com.intellij.ui.table.TableView
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.traceLogViewer.viewer.TraceLogView
import org.jetbrains.plugins.scala.traceLogger.TraceLogger

import java.awt.BorderLayout
import java.awt.event.{HierarchyEvent, MouseAdapter, MouseEvent}
import java.nio.file.Files
import javax.swing.RowSorter.SortKey
import javax.swing.{JPanel, SortOrder}
import scala.jdk.CollectionConverters.*

object TraceLogSelectionView {
  private val ActionToolbarPlace = "scala-trace-log-selection-view-actionbar"

  @Nls
  val displayName: String = NlsString.force("Logs")

  def create(toolWindow: ToolWindow): Content = {
    val actionToolbarPanel = new JPanel
    val actionGroup = new DefaultActionGroup(
      new RefreshAction,
      new OpenLogAction,
      new ClearLogDirectoryAction,
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

  def refresh(openNewItem: Boolean = false): Unit =
    ApplicationManager.getApplication.invokeLater(() => {
      TraceLogSelectionModel.refresh() match {
        case Some(path) if openNewItem =>
          TraceLogView.openTraceLog(path)
        case _ =>
      }
    })

  class RefreshAction extends AnAction with DumbAware {
    getTemplatePresentation.setIcon(AllIcons.Actions.Refresh)

    override def actionPerformed(e: AnActionEvent): Unit =
      refresh()
  }

  class OpenLogAction extends AnAction with DumbAware {
    getTemplatePresentation.setIcon(AllIcons.Actions.MenuOpen)

    override def actionPerformed(e: AnActionEvent): Unit = {
      val selectedFiles = FileChooser.chooseFiles(
        new FileTypeDescriptor(NlsString.force("Select log file"), "log", "txt", "json"),
          null,
          VfsUtil.findFile(TraceLogger.loggerOutputPath, true
        )
      )

      selectedFiles match {
        case Array(logFile) if logFile.exists() => TraceLogView.openTraceLog(logFile.toNioPath)
        case _ =>
      }
    }
  }

  class ClearLogDirectoryAction extends AnAction with DumbAware {
    getTemplatePresentation.setIcon(AllIcons.Actions.GC)

    override def actionPerformed(e: AnActionEvent): Unit = {
      val dir = TraceLogger.loggerOutputPath
      Files.list(dir)
        .forEach(Files.delete(_))
      refresh()
    }
  }
}
