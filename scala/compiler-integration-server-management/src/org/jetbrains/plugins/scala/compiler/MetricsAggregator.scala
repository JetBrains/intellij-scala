package org.jetbrains.plugins.scala.compiler

import org.jetbrains.jps.incremental.scala.remote.{CompileServerMeteringInfo, CompileServerMetrics}

/**
 * Simple thread-safe class which aggregates instances of
 * [[org.jetbrains.jps.incremental.scala.remote.CompileServerMetrics]] into a final instance of
 * [[org.jetbrains.jps.incremental.scala.remote.CompileServerMeteringInfo]].
 */
private class MetricsAggregator {
  private var maxParallelism: Int = 0
  private var maxHeapSizeMb: Int = 0

  def update(metrics: CompileServerMetrics): Unit = synchronized {
    maxParallelism = math.max(maxParallelism, metrics.currentParallelism)
    val currentHeapSizeMb = (metrics.currentHeapSize / 1024 / 1024).toInt
    maxHeapSizeMb = math.max(maxHeapSizeMb, currentHeapSizeMb)
  }

  def result(): CompileServerMeteringInfo = synchronized {
    CompileServerMeteringInfo(maxParallelism, maxHeapSizeMb)
  }
}
