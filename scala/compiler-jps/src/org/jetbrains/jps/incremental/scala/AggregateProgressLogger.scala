package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.plugins.scala.compiler.CompilationUnitId

object AggregateProgressLogger {

  private var state: Map[CompilationUnitId, Double] = Map.empty

  def log(context: CompileContext, unitId: CompilationUnitId, done: Double): Unit = {
    state += unitId -> done

    val compilingNow = state.toSeq
      .filter { case (_, done) => done > 0.0 && done < 1.0 }
      .sortBy { case (CompilationUnitId(moduleId, testScope), _) => (moduleId, testScope) }
    if (compilingNow.nonEmpty) {
      val message = compilingNow
        .map { case (CompilationUnitId(moduleId, testScope), done) =>
          val moduleSuffix = if (testScope) "(test)" else ""
          val donePercent = math.round(done * 100)
          s"$moduleId$moduleSuffix:$donePercent%"
        }.mkString(" ")
      context.processMessage(new ProgressMessage(message, -1.0F))
    }
  }
}
