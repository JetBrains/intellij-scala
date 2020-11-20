package org.jetbrains.plugins.scala.compilationCharts

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}

private class UpdateCompilationProgressListener(project: Project)
  extends CompilerEventListener {

  override def eventReceived(event: CompilerEvent): Unit = {
    val timestamp = System.nanoTime()
    val state = CompilationProgressStateManager.get(project)
    val newStateOption = for {
      compilationUnitId <- event.compilationUnitId
      newProgressInfo <- event match {
        case _: CompilerEvent.CompilationStarted =>
          Some(CompilationProgressInfo(
            startTime = timestamp,
            finishTime = None,
            updateTime = timestamp,
            progress = 0.0
          ))
        case CompilerEvent.ProgressEmitted(_, _, progress) =>
          state.get(compilationUnitId).map(_.copy(
            updateTime = timestamp,
            progress = progress
          ))
        case _: CompilerEvent.CompilationFinished =>
          state.get(compilationUnitId).map(_.copy(
            finishTime = Some(timestamp),
            updateTime = timestamp,
            progress = 1.0
          ))
        case _ =>
          None
      }
    } yield state.updated(compilationUnitId, newProgressInfo)
    newStateOption.foreach(CompilationProgressStateManager.update(project, _))
  }
}
