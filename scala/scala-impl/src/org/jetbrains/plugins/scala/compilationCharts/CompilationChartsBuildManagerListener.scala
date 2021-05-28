package org.jetbrains.plugins.scala.compilationCharts

import java.util.UUID
import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.components.{ComponentManager, Service, ServiceManager}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compilationCharts.ui.CompilationChartsComponentHolder
import org.jetbrains.plugins.scala.compiler.CompileServerClient
import org.jetbrains.plugins.scala.util.ScheduledService

import scala.concurrent.duration.DurationInt

class CompilationChartsBuildManagerListener
  extends BuildManagerListener
    with CompileTask {

  // It runs BEFORE compilation.
  // We use it instead of buildStarted or beforeBuildProcessStarted methods to avoid an exception (EA-263973).
  // Also it's an optimization. We need to schedule updates only for compilation not for UP_TO_DATE_CHECK.
  override def execute(compileContext: CompileContext): Boolean = {
    val project = compileContext.getProject
    CompilationProgressStateManager.erase(project)
    CompileServerMetricsStateManager.reset(project)

    val updater = CompilationChartsUpdater.get(project)
    updater.stopScheduling()
    updater.startScheduling()
    true
  }

  override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
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
