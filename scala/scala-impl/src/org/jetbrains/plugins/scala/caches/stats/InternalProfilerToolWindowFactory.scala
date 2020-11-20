package org.jetbrains.plugins.scala.caches.stats

import java.awt.BorderLayout
import java.util.concurrent.{Future, TimeUnit}

import com.intellij.concurrency.JobScheduler
import com.intellij.icons.AllIcons
import com.intellij.notification.{NotificationGroup, NotificationType}
import com.intellij.openapi.actionSystem.{ActionManager, AnAction, AnActionEvent, DefaultActionGroup}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}
import com.intellij.ui.content.{Content, ContentFactory}
import javax.swing.{Icon, JPanel, JScrollPane}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.plugins.scala.util.ui.extensions.ComponentExt

import scala.jdk.CollectionConverters._

class InternalProfilerToolWindowFactory extends ToolWindowFactory with DumbAware {

  override def init(toolWindow: ToolWindow): Unit = {
    toolWindow.setStripeTitle("Scala plugin profiler")
  }

  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    InternalProfilerToolWindowFactory.createContent(project).foreach {
      toolWindow.getContentManager.addContent
    }
  }

  override def isDoNotActivateOnStart: Boolean = true

  override def shouldBeAvailable(project: Project): Boolean =
    ApplicationManager.getApplication.isInternal
}

//noinspection ScalaExtractStringToBundle
object InternalProfilerToolWindowFactory {
  val ID = "internal-profiler"
  def notificationGroup: NotificationGroup = ScalaNotificationGroups.balloonGroup

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

  lazy val memoryModel: DataByIdTableModel[MemoryData] = {
    val dataById = new DataById[MemoryData](_.id)
    new DataByIdTableModel(
      dataById,
      dataById.stringColumn("Computation", _.name),
      dataById.numColumn("Tracked caches", _.trackedCaches),
      dataById.numColumn("Tracked cache entries", _.trackedCacheEntries)
    )(preferredWidths = Seq(5, 1, 1))
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


  def createContent(project: Project): Seq[Content] = {
    val timingsTable = createTableWithToolbarPanel(ScalaCacheTracerDataSource, timingsModel, project)
    val parentCalls = createTableWithToolbarPanel(ScalaCacheTracerDataSource, parentCallsModel, project)
    val memory = createTableWithToolbarPanel(ScalaCacheMemoryDataSource, memoryModel, project)

    val factory = ContentFactory.SERVICE.getInstance()
    Seq(
      factory.createContent(timingsTable, "Timings", false),
      factory.createContent(parentCalls, "Parent Calls", false),
      factory.createContent(memory, "Memory", false)
    )
  }

  def createTableWithToolbarPanel[Data](dataSource: DataSource[Data], tableModel: DataByIdTableModel[Data], project: Project): JPanel = {
    val actionToolbarPanel = new JPanel
    val actionGroup = new DefaultActionGroup(
      new RunPauseAction(dataSource),
      new ClearAction(dataSource, tableModel),
      new CacheContentClearAction(project)
    )
    val actionToolBar = ActionManager.getInstance().createActionToolbar(ID, actionGroup, false)
    actionToolbarPanel.setLayout(new BorderLayout)
    actionToolbarPanel.add(actionToolBar.getComponent)

    val table = tableModel.createTable()
    tableModel.registerSpeedSearch(table)

    val scrollPane = new JScrollPane
    scrollPane.setViewportView(table)

    val mainPanel = new JPanel()
    mainPanel.setLayout(new BorderLayout)
    mainPanel.add(actionToolbarPanel, BorderLayout.WEST)
    mainPanel.add(scrollPane, BorderLayout.CENTER)

    mainPanel.bindExecutionToVisibility { () =>
      scheduleRefresh(tableModel, dataSource)
    }

    mainPanel
  }

  def scheduleRefresh[Data](tableModel: DataByIdTableModel[Data], dataSource: DataSource[Data]): Future[_] = {
    val refreshRateMs = 500L

    JobScheduler.getScheduler
      .scheduleWithFixedDelay(() => {
        ApplicationManager.getApplication.invokeLater(() =>
          tableModel.refresh(dataSource.getCurrentData)
        )
      }, 0, refreshRateMs, TimeUnit.MILLISECONDS)
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

    override def actionPerformed(e: AnActionEvent): Unit = {
      if (dataSource.isActive) dataSource.stop()
      else dataSource.resume()
    }
  }


  class ClearAction(dataSource: DataSource[_], tableModel: DataByIdTableModel[_]) extends AnAction with DumbAware {
    getTemplatePresentation.setIcon(AllIcons.Actions.GC)

    override def actionPerformed(e: AnActionEvent): Unit = {
      dataSource.clear()
      tableModel.clear()
    }
  }

  class CacheContentClearAction(project: Project) extends AnAction with DumbAware {
    getTemplatePresentation.setIcon(AllIcons.Actions.Uninstall)
    getTemplatePresentation.setText("Clear Cache Contents")
    // AllIcons.Actions.Uninstall
    // AllIcons.Actions.Unselectall

    override def actionPerformed(e: AnActionEvent): Unit = {
      def toMB(bytes: Double) = bytes / (1024.0 * 1024.0)
      def toPercent(d: Double) = d * 100.0
      val runtime = Runtime.getRuntime
      def usedMemory() = toMB((runtime.totalMemory - runtime.freeMemory).toDouble)

      val memoryBeforePrepare = usedMemory()
      System.gc()
      val cacheEntityCount = CacheTracker.tracked.values.foldLeft(0)(_ + _.cachedEntityCount)
      val memoryBeforeCacheFreeing = usedMemory()
      CacheTracker.clearAllCaches()
      System.gc()
      val memoryAfter = usedMemory()

      val preFreed = memoryBeforePrepare - memoryBeforeCacheFreeing
      val cacheFreed = memoryBeforeCacheFreeing - memoryAfter
      val totalFreed = preFreed + cacheFreed
      val total = toMB(runtime.totalMemory.toDouble)

      notificationGroup.createNotification(
        s"Cache cleared ($cacheEntityCount entities)",
        f"""<html>
           |<body>
           |<table>
           |<tr><td>Pre GC: </td><td>$preFreed%1.1f MB</td><td>(${toPercent(preFreed/memoryBeforePrepare)}%1.1f%%)</td></tr>
           |<tr><td>Cache GC: </td><td>$cacheFreed%1.1f MB</td><td>(${toPercent(cacheFreed/memoryBeforePrepare)}%1.1f%%)</td></tr>
           |<tr><td>Total GC: </td><td>$totalFreed%1.1f MB</td><td>(${toPercent(totalFreed / memoryBeforePrepare)}%1.1f%%)</td></tr>
           |<tr><td>Used before: </td><td>$memoryBeforePrepare%1.1f MB</td><td></td></tr>
           |<tr><td>Used after: </td><td>$memoryAfter%1.1f MB</td><td></td></tr>
           |<tr><td>Available: </td><td>$total%1.1f MB</td><td></td></tr>
           |</table>
           |</body>
           |</html>""".stripMargin,
        NotificationType.INFORMATION,
        null
      ).notify(project)
    }
  }
}