package org.jetbrains.plugins.scala.caches.stats

import java.awt.BorderLayout
import java.util.Comparator
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import com.intellij.concurrency.JobScheduler
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionManager, AnAction, AnActionEvent, DefaultActionGroup}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}
import com.intellij.ui.content.{Content, ContentFactory}
import com.intellij.ui.table.TableView
import com.intellij.util.ui.{ColumnInfo, ListTableModel}
import javax.swing.{Icon, JPanel, JScrollPane}
import org.jetbrains.plugins.scala.caches.stats.InternalProfilerToolWindowFactory.scheduleRefresh
import org.jetbrains.plugins.scala.caches.stats.TracerTableModel.{MyColumnInfo, map}

import scala.collection.JavaConverters._

class InternalProfilerToolWindowFactory extends ToolWindowFactory with DumbAware {

  override def init(toolWindow: ToolWindow): Unit = {
    toolWindow.setStripeTitle("Internal Profiler")
  }

  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    InternalProfilerToolWindowFactory.createContent().foreach {
      toolWindow.getContentManager.addContent
    }
    scheduleRefresh()
  }

  override def isDoNotActivateOnStart: Boolean = true
}

object InternalProfilerToolWindowFactory {
  val ID = "internal-profiler"


  def createContent(): Seq[Content] = {
    val timingsTable = createTableWithToolbarPanel(TracerTableModel.timings)
    val parentCalls = createTableWithToolbarPanel(TracerTableModel.parentCalls)

    val factory = ContentFactory.SERVICE.getInstance()
    Seq(
      factory.createContent(timingsTable, "Timings", false),
      factory.createContent(parentCalls, "Parent Calls", false)
    )
  }

  def createTableWithToolbarPanel(tableModel: TracerTableModel): JPanel = {
    val actionToolbarPanel = new JPanel
    val actionGroup = new DefaultActionGroup(RunPauseAction, ClearAction)
    val actionToolBar = ActionManager.getInstance().createActionToolbar(ID, actionGroup, false)
    actionToolbarPanel.setLayout(new BorderLayout)
    actionToolbarPanel.add(actionToolBar.getComponent)

    val table = new TracerTable(tableModel)

    val scrollPane = new JScrollPane
    scrollPane.setViewportView(table)

    val mainPanel = new JPanel()
    mainPanel.setLayout(new BorderLayout)
    mainPanel.add(actionToolbarPanel, BorderLayout.WEST)
    mainPanel.add(scrollPane, BorderLayout.CENTER)

    mainPanel
  }


  def scheduleRefresh(): Unit = {
    JobScheduler.getScheduler
      .scheduleWithFixedDelay(() => {
        ApplicationManager.getApplication.invokeLater(() =>
          TracerTableModel.refresh()
        )
      }, 500L, 500L, TimeUnit.MILLISECONDS)
  }

  object RunPauseAction extends AnAction with DumbAware {

    private def currentIcon(): Icon = {
      if (Tracer.isEnabled)
        AllIcons.Actions.Pause
      else AllIcons.Actions.Resume
    }

    override def update(e: AnActionEvent): Unit = {
      e.getPresentation.setIcon(currentIcon())
    }

    def actionPerformed(e: AnActionEvent): Unit = {
      Tracer.setEnabled(!Tracer.isEnabled)
    }
  }


  object ClearAction extends AnAction with DumbAware {
    getTemplatePresentation.setIcon(AllIcons.Actions.GC)

    def actionPerformed(e: AnActionEvent): Unit = {
      Tracer.clearAll()
      TracerTableModel.clear()
    }
  }

}

private class TracerTable(model: TracerTableModel) extends TableView(model) {
  fixColumnWidth()

  private def fixColumnWidth(): Unit = {
    val wideIdx = model.columns.indexWhere(_.wide)
    if (wideIdx >= 0) {
      getColumnModel.getColumn(wideIdx)
        .setPreferredWidth(1000)
    }
  }
}

private class TracerTableModel(val columns: Seq[MyColumnInfo[_]])
  extends ListTableModel[String](columns: _*) {

  def refresh(): Unit = {
    val data = Tracer.getCurrentData

    data.forEach { d =>
      val tracerId = d.id
      map.put(tracerId, d)

      indexOf(tracerId) match {
        case -1  => addRow(tracerId)
        case idx => fireTableRowsUpdated(idx, idx)
      }
    }
  }

}

private object TracerTableModel {
  private val map = new ConcurrentHashMap[String, TracerData]()

  lazy val timings = new TracerTableModel(columnsWithTimings)
  lazy val parentCalls = new TracerTableModel(columnsWithParentCalls)

  class MyColumnInfo[T: Ordering](name: String,
                                  value: TracerData => T,
                                  val wide: Boolean) extends ColumnInfo[String, T](name) {

    override def valueOf(id: String): T = value(map.get(id))

    override def getComparator: Comparator[String] = implicitly[Ordering[T]].on(valueOf)
  }

  def clear(): Unit = {
    map.clear()
    timings.setItems(new java.util.ArrayList())
    parentCalls.setItems(new java.util.ArrayList())
  }

  def refresh(): Unit = {
    timings.refresh()
    parentCalls.refresh()
  }

  private def columnsWithTimings = Seq[MyColumnInfo[_]](
    column("Computation", _.name),
    column("Invoked", _.totalCount),
    column("Read from cache", _.fromCacheCount),
    column("Actually computed", _.actualCount),
    column("Max Time, ms", _.maxTime),
    column("Total Time, ms", _.totalTime),
    column("Own Time, ms", _.ownTime),
    column("Avg Time, ms", _.avgTime)
  )

  private def columnsWithParentCalls = Seq[MyColumnInfo[_]](
    column("Computation", _.name),
    column("Actually computed", _.actualCount),
    column("Total Time, ms", _.totalTime),
    column("Parent calls", parentCallsText, wide = true)
  )


  private def column[T: Ordering](name: String,
                                  value: TracerData => T,
                                  wide: Boolean = false): MyColumnInfo[T] =
    new MyColumnInfo[T](name, value, wide)


  private def parentCallsText(data: TracerData): String = {
    val parentCalls = data.parentCalls
    val sorted = parentCalls.asScala.sortBy(_._2).reverse
    val total = sorted.map(_._2).sum
    def fraction(i: Int): String = (i.toDouble / total * 100).round.toString + " %"

    sorted.map {
      case (name, count) => s"$name: ${fraction(count)}"
    }.mkString("; ")
  }
}