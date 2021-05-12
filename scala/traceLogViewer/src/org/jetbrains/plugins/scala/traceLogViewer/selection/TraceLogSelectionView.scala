package org.jetbrains.plugins.scala.traceLogViewer.selection

import com.intellij.openapi.actionSystem.{ActionManager, DefaultActionGroup}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.{Content, ContentFactory}
import com.intellij.ui.table.TableView
import com.intellij.util.ui.{ColumnInfo, ListTableModel}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.traceLogViewer.viewer.TraceLogView
import org.jetbrains.plugins.scala.traceLogger.TraceLogger

import java.awt.BorderLayout
import java.awt.event.{HierarchyEvent, MouseAdapter, MouseEvent}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, ZoneId}
import java.util.Comparator
import javax.swing.RowSorter.SortKey
import javax.swing.{JPanel, SortOrder}
import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._
import scala.util.Try

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

    val table = new TableView(LogListModel)
    table.addMouseListener(new MouseAdapter {
      override def mouseClicked(e: MouseEvent): Unit =
        if (e.getClickCount == 2) {
          Option(table.getSelectedObject)
            .foreach(entry => TraceLogView.openTraceLog(entry.path, toolWindow))
        }
    })
    table.getRowSorter.setSortKeys(Seq(new SortKey(1, SortOrder.DESCENDING)).asJava)
    //tableModel.registerSpeedSearch(table)

    val scrollPane = new JBScrollPane
    scrollPane.setViewportView(table)

    val mainPanel = new JPanel()
    mainPanel.setLayout(new BorderLayout)
    mainPanel.add(actionToolbarPanel, BorderLayout.WEST)
    mainPanel.add(scrollPane, BorderLayout.CENTER)

    mainPanel.addHierarchyListener((e: HierarchyEvent) => {
      if (HierarchyEvent.SHOWING_CHANGED == (e.getChangeFlags & HierarchyEvent.SHOWING_CHANGED))
        LogListModel.refresh()
    })

    val factory = ContentFactory.SERVICE.getInstance()
    factory.createContent(mainPanel, displayName, true)
  }

  def refresh(): Unit =
    ApplicationManager.getApplication.invokeLater(() => LogListModel.refresh())

  private def listEntries(): Seq[Entry] = {
    val paths = Try(Files.list(TraceLogger.loggerOutputPath).toScala(Seq))
      .getOrElse(Seq.empty)
    for (path <- paths) yield {
      val attr = Try(Files.readAttributes(path, classOf[BasicFileAttributes])).toOption
      Entry(
        path.getFileName.toString,
        path,
        Instant.ofEpochMilli(attr.fold(0L)(_.lastModifiedTime().toMillis))
      )
    }
  }

  private case class Entry(name: String, path: Path, date: Instant)
  private object Entry {
    def nameColumn: ColumnInfo[Entry, String] = new ColumnInfo[Entry, String]("Log") {
      override def valueOf(item: Entry): String = item.name

      override def getComparator: Comparator[Entry] = Comparator.comparing(_.name)
    }

    def dateColumn: ColumnInfo[Entry, String] = new ColumnInfo[Entry, String]("Created") {
      private val formatter =
        DateTimeFormatter.ofPattern("hh:mm  (()) (dd.MM.yyyy)")
          .withZone(ZoneId.systemDefault())

      override def valueOf(item: Entry): String = {
        val date = item.date
        val daysAgo = Duration.between(date, Instant.now()).toDays
        val ago = daysAgo match {
          case 0 => "today"
          case 1 => "yesterday"
          case _ => s""
        }

        formatter.format(item.date).replace("(())", ago)
      }

      override def getComparator: Comparator[Entry] = Comparator.comparing(_.date)
    }
  }

  private object LogListModel extends ListTableModel[Entry](Entry.nameColumn, Entry.dateColumn) {
    def refresh(): Unit = {
      setItems(listEntries().asJava)
    }
  }
}
