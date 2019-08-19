package org.jetbrains.plugins.scala.caches.stats

import java.awt.BorderLayout
import java.util.concurrent.TimeUnit

import com.intellij.concurrency.JobScheduler
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{ActionManager, AnAction, AnActionEvent, DefaultActionGroup}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}
import com.intellij.ui.content.{Content, ContentFactory}
import javax.swing.{Icon, JPanel, JScrollPane}

import scala.collection.JavaConverters._

class InternalProfilerToolWindowFactory extends ToolWindowFactory with DumbAware {

  override def init(toolWindow: ToolWindow): Unit = {
    toolWindow.setStripeTitle("Internal Profiler")
  }

  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    InternalProfilerToolWindowFactory.createContent().foreach {
      toolWindow.getContentManager.addContent
    }
  }

  override def isDoNotActivateOnStart: Boolean = true
}

object InternalProfilerToolWindowFactory {
  val ID = "internal-profiler"

  lazy val timingsModel: DataByIdTableModel[TracerData] = {
    val dataById = new DataById[TracerData](_.id)
    new DataByIdTableModel(dataById,
      dataById.stringColumn("Computation", _.name),
      dataById.numColumn("Invoked", _.totalCount),
      dataById.numColumn("Read from cache", _.fromCacheCount),
      dataById.numColumn("Actually computed", _.actualCount),
      dataById.numColumn("Max Time, ms", _.maxTime),
      dataById.numColumn("Total Time, ms", _.totalTime),
      dataById.numColumn("Own Time, ms", _.ownTime),
      dataById.numColumn("Avg Time, ms", _.avgTime)
    )(preferredWidths = Seq(5, 1, 1, 1, 1, 1, 1, 1))
  }

  lazy val parentCallsModel: DataByIdTableModel[TracerData] = {
    val dataById = new DataById[TracerData](_.id)
    new DataByIdTableModel(dataById,
      dataById.stringColumn("Computation", _.name),
      dataById.numColumn("Actually computed", _.actualCount),
      dataById.numColumn("Total Time, ms", _.totalTime),
      dataById.stringColumn("Parent calls", parentCallsText)
    )(preferredWidths = Seq(6, 1, 1, 12))
  }

  private def parentCallsText(data: TracerData): String = {
    val parentCalls = data.parentCalls
    val sorted = parentCalls.asScala.sortBy(_._2).reverse
    val total = sorted.map(_._2).sum
    def fraction(i: Int): String = (i.toDouble / total * 100).round.toString + " %"

    sorted.map {
      case (name, count) => s"$name: ${fraction(count)}"
    }.mkString("; ")
  }


  def createContent(): Seq[Content] = {
    val timingsTable = createTableWithToolbarPanel(ScalaCacheTracerDataSource, timingsModel)
    val parentCalls = createTableWithToolbarPanel(ScalaCacheTracerDataSource, parentCallsModel)

    val factory = ContentFactory.SERVICE.getInstance()
    Seq(
      factory.createContent(timingsTable, "Timings", false),
      factory.createContent(parentCalls, "Parent Calls", false)
    )
  }

  def createTableWithToolbarPanel[Data](dataSource: DataSource[Data], tableModel: DataByIdTableModel[Data]): JPanel = {
    val actionToolbarPanel = new JPanel
    val actionGroup = new DefaultActionGroup(new RunPauseAction(dataSource), new ClearAction(dataSource, tableModel))
    val actionToolBar = ActionManager.getInstance().createActionToolbar(ID, actionGroup, false)
    actionToolbarPanel.setLayout(new BorderLayout)
    actionToolbarPanel.add(actionToolBar.getComponent)

    val table = tableModel.createTable()
    tableModel.registerSpeedSearch(table)
    scheduleRefresh(tableModel, dataSource)

    val scrollPane = new JScrollPane
    scrollPane.setViewportView(table)

    val mainPanel = new JPanel()
    mainPanel.setLayout(new BorderLayout)
    mainPanel.add(actionToolbarPanel, BorderLayout.WEST)
    mainPanel.add(scrollPane, BorderLayout.CENTER)

    mainPanel
  }

  def scheduleRefresh[Data](tableModel: DataByIdTableModel[Data], dataSource: DataSource[Data]): Unit = {
    val refreshRateMs = 500L

    JobScheduler.getScheduler
      .scheduleWithFixedDelay(() => {
        ApplicationManager.getApplication.invokeLater(() =>
          tableModel.refresh(dataSource.getCurrentData)
        )
      }, refreshRateMs, refreshRateMs, TimeUnit.MILLISECONDS)
  }


  class RunPauseAction(dataSource: DataSource[_]) extends AnAction with DumbAware {

    private def currentIcon(): Icon = {
      if (dataSource.isActive)
        AllIcons.Actions.Pause
      else AllIcons.Actions.Resume
    }

    override def update(e: AnActionEvent): Unit = {
      e.getPresentation.setIcon(currentIcon())
    }

    def actionPerformed(e: AnActionEvent): Unit = {
      if (dataSource.isActive) dataSource.stop()
      else dataSource.resume()
    }
  }


  class ClearAction(dataSource: DataSource[_], tableModel: DataByIdTableModel[_]) extends AnAction with DumbAware {
    getTemplatePresentation.setIcon(AllIcons.Actions.GC)

    def actionPerformed(e: AnActionEvent): Unit = {
      dataSource.clear()
      tableModel.clear()
    }
  }
}