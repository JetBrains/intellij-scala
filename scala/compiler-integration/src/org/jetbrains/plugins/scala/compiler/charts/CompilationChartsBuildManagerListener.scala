package org.jetbrains.plugins.scala.compiler.charts

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.charts.ui.CompilationChartsComponentHolder
import org.jetbrains.plugins.scala.compiler.{CompileServerClient, CompileServerLauncher}
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.plugins.scala.util.ScheduledService

import java.util.UUID
import scala.concurrent.duration.DurationInt

class CompilationChartsBuildManagerListener
  extends BuildManagerListener
    with CompileTask {

  // It runs BEFORE compilation.
  // We use it instead of buildStarted or beforeBuildProcessStarted methods to avoid an exception (EA-263973).
  // Also it's an optimization. We need to schedule updates only for compilation not for UP_TO_DATE_CHECK.
  override def execute(compileContext: CompileContext): Boolean = {
    if (isUnitTestMode)
      return true

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

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
    if (!project.isDisposed) {
      CompilationChartsUpdater.get(project).stopScheduling()
    }
  }
}

@Service(Array(Service.Level.PROJECT))
private final class CompilationChartsUpdater(project: Project)
  extends ScheduledService(1.seconds) {

  override def runnable: Runnable = { () =>
    if (CompileServerLauncher.running) {
      val currentTime = System.nanoTime()
      updateCompileServerMetricsState(currentTime)
      refreshDiagram()
    }
    else {
      stopScheduling()
    }
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
    project.getService(classOf[CompilationChartsUpdater])
}
