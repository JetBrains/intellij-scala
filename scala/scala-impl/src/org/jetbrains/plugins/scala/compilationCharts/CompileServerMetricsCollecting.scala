package org.jetbrains.plugins.scala.compilationCharts

import java.util.UUID

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.CompileServerClient
import org.jetbrains.plugins.scala.util.ScheduledService

import scala.concurrent.duration.DurationInt

class CompileServerMetricsCollecting
  extends CompileTask
    with BuildManagerListener {

  // BEFORE
  override def execute(context: CompileContext): Boolean = {
    val project = context.getProject
    CompileServerMetricsStateManager.reset(project)
    val collector = CompileServerMetricsCollector.get(project)
    collector.stopScheduling()
    collector.startScheduling()
    true
  }

  override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit =
    CompileServerMetricsCollector.get(project).stopScheduling()
}

@Service
private final class CompileServerMetricsCollector(project: Project)
  extends ScheduledService(3.seconds) {

  private val compileServerClient = CompileServerClient.get(project)

  override def runnable: Runnable = { () =>
    val metrics = compileServerClient.getMetrics()
    val timestamp = System.nanoTime()
    val state = CompileServerMetricsStateManager.get(project)
    val newState = state.copy(
      maxHeapSize = metrics.maxHeapSize,
      heapUsed = state.heapUsed.updated(timestamp, metrics.heapUsed)
    )
    CompileServerMetricsStateManager.update(project, newState)
  }
}

private object CompileServerMetricsCollector {

  def get(project: Project): CompileServerMetricsCollector =
    ServiceManager.getService(project, classOf[CompileServerMetricsCollector])
}