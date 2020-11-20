package org.jetbrains.plugins.scala.compilationCharts.ui

import org.jetbrains.plugins.scala.compilationCharts.{CompilationProgressInfo, CompilationProgressState, CompileServerMemoryState, Memory, Timestamp}
import org.jetbrains.plugins.scala.compiler.{CompilationUnitId, ScalaCompileServerSettings}

import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}

final case class Diagrams(progressDiagram: ProgressDiagram,
                          memoryDiagram: MemoryDiagram,
                          progressTime: FiniteDuration)

object Diagrams {

  def calculate(progressState: CompilationProgressState,
                metricsState: CompileServerMemoryState): Diagrams = {
    val progressRowCount = getParallelism
    val result = for {
      (minTimestamp, maxTimestamp) <- getMinMaxTimestamps(progressState)
      progressDiagram = calculateProgressDiagram(progressState, progressRowCount, minTimestamp, maxTimestamp)
      progressTime <- progressDiagram.segmentGroups.flatten.map(_.to).maxOption
      memoryDiagram = calculateMemoryDiagram(metricsState, minTimestamp, maxTimestamp)
    } yield Diagrams(
      progressDiagram = progressDiagram,
      memoryDiagram = memoryDiagram,
      progressTime = progressTime
    )
    result.getOrElse(Diagrams(
      ProgressDiagram(Seq.empty, progressRowCount),
      MemoryDiagram(Seq.empty, metricsState.maxHeapSize),
      Duration.Zero
    ))
  }

  private def getParallelism: Int = {
    val settings = ScalaCompileServerSettings.getInstance
    if (settings.COMPILE_SERVER_PARALLEL_COMPILATION) settings.COMPILE_SERVER_PARALLELISM else 1
  }

  private def getMinMaxTimestamps(progressState: CompilationProgressState): Option[(Timestamp, Timestamp)] = {
    val timestamps = progressState.values.flatMap { info =>
      Seq(info.startTime, info.updateTime) ++ info.finishTime.toSeq
    }
    for {
      min <- timestamps.minOption
      max <- timestamps.maxOption
    } yield (min, max)
  }

  private def calculateProgressDiagram(progressState: CompilationProgressState,
                                       rowCount: Int,
                                       minTimestamp: Timestamp,
                                       maxTimestamp: Timestamp): ProgressDiagram = {
    def groupSegments(segments: Seq[Segment]): Seq[Seq[Segment]] = {
      @tailrec
      def rec(groups: Seq[Seq[Segment]],
              segments: Seq[Segment]): Seq[Seq[Segment]] = segments match {
        case Seq() => groups
        case Seq(interval, remainIntervals@_*) => rec(insert(groups, interval), remainIntervals)
      }

      def insert(groups: Seq[Seq[Segment]],
                 segment: Segment): Seq[Seq[Segment]] = groups match {
        case Seq() =>
          Seq(Seq(segment))
        case Seq(group, remainGroups@_*) =>
          if (group.last.to < segment.from)
            (group :+ segment) +: remainGroups
          else
            group +: insert(remainGroups, segment)
      }

      rec(Seq.empty, segments)
    }

    val sortedState = progressState.toSeq.sortBy(_._2.startTime)
    val segments = sortedState.flatMap { case (unitId, CompilationProgressInfo(startTime, finishTime, _, progress)) =>
      val from = (startTime - minTimestamp).nanos
      val to = (finishTime.getOrElse(maxTimestamp) - minTimestamp).nanos
      if (from.length >= 0 && to.length >= 0)
        Some(Segment(
          unitId = unitId,
          from = from,
          to = to,
          progress = progress
        ))
      else
        None
    }
    ProgressDiagram(groupSegments(segments), rowCount)
  }

  private def calculateMemoryDiagram(metricsState: CompileServerMemoryState,
                                     minTimestamp: Timestamp,
                                     maxTimestamp: Timestamp): MemoryDiagram = {
    val points = metricsState.heapUsed.map { case (timestamp, memory) =>
      val fixedTimestamp = if (timestamp < minTimestamp)
        minTimestamp
      else if (timestamp > maxTimestamp)
        maxTimestamp
      else
        timestamp
      fixedTimestamp -> memory
    }.map { case (timestamp, memory) =>
      val time = (timestamp - minTimestamp).nanos
      MemoryPoint(time, memory)
    }.toSeq.sortBy(_.time)

    val maxMemory = metricsState.maxHeapSize
    MemoryDiagram(points, maxMemory)
  }
}

final case class ProgressDiagram(segmentGroups: Seq[Seq[Segment]],
                                 rowCount: Int)

final case class Segment(unitId: CompilationUnitId,
                         from: FiniteDuration,
                         to: FiniteDuration,
                         progress: Double)

final case class MemoryDiagram(points: Seq[MemoryPoint],
                               maxMemory: Memory)

final case class MemoryPoint(time: FiniteDuration,
                             memory: Memory)
