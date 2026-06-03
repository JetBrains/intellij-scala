package org.jetbrains.plugins.scala.compiler.charts

import org.jetbrains.plugins.scala.compiler.CompilationUnitId

class CompilationProgressState private(state: Map[CompilationUnitId, Map[Timestamp, CompilationProgressInfo]]) {

  def put(unitId: CompilationUnitId, progressInfo: CompilationProgressInfo): CompilationProgressState = {
    val compilationUnitMap = state.getOrElse(unitId, Map.empty)
    val updatedCompilationUnitMap = compilationUnitMap.updated(progressInfo.startTime, progressInfo)
    val updatedState = state.updated(unitId, updatedCompilationUnitMap)
    new CompilationProgressState(updatedState)
  }

  def updateLast(unitId: CompilationUnitId)
                (f: CompilationProgressInfo => CompilationProgressInfo): CompilationProgressState = {
    val compilationUnitMap = state.getOrElse(unitId, Map.empty)
    val updatedCompilationUnitMap = for {
      maxStartTime <- compilationUnitMap.keys.maxOption
      progressInfo <- compilationUnitMap.get(maxStartTime)
    } yield compilationUnitMap.updated(maxStartTime, f(progressInfo))
    val resultCompilationUnitMap = updatedCompilationUnitMap.getOrElse(compilationUnitMap)
    val updatedState = state.updated(unitId, resultCompilationUnitMap)
    new CompilationProgressState(updatedState)
  }

  def toSeq: Seq[(CompilationUnitId, CompilationProgressInfo)] =
    state.toSeq.flatMap { case (unitId, progressInfoMap) =>
      progressInfoMap.values.map(unitId -> _)
    }
}

object CompilationProgressState {

  final val Empty = new CompilationProgressState(Map.empty)
}
