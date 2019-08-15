package org.jetbrains.plugins.scala.caches.stats

import java.awt.BorderLayout
import java.util.Comparator
import java.util.concurrent.TimeUnit

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
import org.jetbrains.plugins.scala.caches.stats.CacheStatsTableModel.columns

class InternalProfilerToolWindowFactory extends ToolWindowFactory with DumbAware {

  override def init(toolWindow: ToolWindow): Unit = {
    toolWindow.setStripeTitle("Internal Profiler")
  }

  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    toolWindow.getContentManager.addContent(InternalProfilerToolWindowFactory.createContent())
  }

  override def isDoNotActivateOnStart: Boolean = true
}

object InternalProfilerToolWindowFactory {
  val ID = "internal-profiler"


  def createContent(): Content = {
    val actionToolbarPanel = new JPanel
    val actionGroup = new DefaultActionGroup(RunPauseAction, ClearAction)
    val actionToolBar = ActionManager.getInstance().createActionToolbar(ID, actionGroup, false)
    actionToolbarPanel.setLayout(new BorderLayout)
    actionToolbarPanel.add(actionToolBar.getComponent)

    val table = new TableView(CacheStatsTableModel.instance)

    val scrollPane = new JScrollPane
    scrollPane.setViewportView(table)

    val mainPanel = new JPanel()
    mainPanel.setLayout(new BorderLayout)
    mainPanel.add(actionToolbarPanel, BorderLayout.WEST)
    mainPanel.add(scrollPane, BorderLayout.CENTER)

    scheduleRefresh()

    ContentFactory.SERVICE.getInstance().createContent(mainPanel, "Tracing", false)
  }

  def scheduleRefresh(): Unit = {
    JobScheduler.getScheduler
      .scheduleWithFixedDelay(() => {
        if (Tracer.isEnabled) CacheStatsTableModel.refresh()
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
      CacheStatsTableModel.refresh()
    }
  }

}


private class CacheStatsTableModel(stats: java.util.List[TracerData])
  extends ListTableModel[TracerData](columns, stats)

private object CacheStatsTableModel {

  lazy val instance = new CacheStatsTableModel(Tracer.getCurrentData)

  def refresh(): Unit = {
    val data = Tracer.getCurrentData
    ApplicationManager.getApplication.invokeLater { () =>
      instance.setItems(data)
    }
  }

  private val columns = Array[ColumnInfo[_, _]](
    column("Computation", _.name),
    column("Invoked", _.totalCount),
    column("Read from cache", _.fromCacheCount),
    column("Actually computed", _.actualCount),
    column("Max Time, ms", _.maxTime),
    column("Avg Time, ms", _.avgTime),
    column("Total Time, ms", _.totalTime)
  )

  private def column[T: Ordering](name: String,
                                  value: TracerData => T): ColumnInfo[TracerData, T] =
    new ColumnInfo[TracerData, T](name) {
      override def valueOf(item: TracerData): T = value(item)

      override def getComparator: Comparator[TracerData] = implicitly[Ordering[T]].on(valueOf)
    }
}