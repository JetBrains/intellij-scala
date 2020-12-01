package org.jetbrains.plugins.scala.compilationCharts

import java.util.UUID
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compilationCharts.ui.CompilationChartsComponentHolder
import org.jetbrains.plugins.scala.compiler.CompileServerClient
import org.jetbrains.plugins.scala.util.ScheduledService

import scala.concurrent.duration.DurationInt

class CompilationChartsBuildManagerListener
  extends BuildManagerListener {

  override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    CompilationProgressStateManager.erase(project)
    CompileServerMetricsStateManager.reset(project)

    val updater = CompilationChartsUpdater.get(project)
    updater.stopScheduling()
    updater.startScheduling()
  }

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    CompilationChartsUpdater.get(project).stopScheduling()
}

@Service
private final class CompilationChartsUpdater(project: Project)
  extends ScheduledService(1.seconds) {

  override def runnable: Runnable = { () =>
    val currentTime = System.nanoTime()
    updateCompileServerMetricsState(currentTime)
    refreshDiagram()
  }

  private def updateCompileServerMetricsState(currentTime: Timestamp): Unit = {
    val metrics = CompileServerClient.get(project).getMetrics()
    val state = CompileServerMetricsStateManager.get(project)
    val newState = state.copy(
      maxHeapSize = metrics.maxHeapSize,
      heapUsed = state.heapUsed.updated(currentTime, metrics.heapUsed)
    )
    CompileServerMetricsStateManager.update(project, newState)
  }

  private def refreshDiagram(): Unit = {
    val component = CompilationChartsComponentHolder.createOrGet(project)
    component.updateData()
    component.repaint()
  }
}

private object CompilationChartsUpdater {

  def get(project: Project): CompilationChartsUpdater =
    ServiceManager.getService(project, classOf[CompilationChartsUpdater])
}
