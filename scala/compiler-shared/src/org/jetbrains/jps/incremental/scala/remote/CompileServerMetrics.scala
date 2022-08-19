package org.jetbrains.jps.incremental.scala.remote

/**
 * @param heapUsed used heap memory in bytes
 * @param currentHeapSize current heap size in bytes
 * @param maxHeapSize maximum heap size in bytes
 * @param currentParallelism the current number of compile commands running in parallel
 */
final case class CompileServerMetrics(
  heapUsed: Long,
  currentHeapSize: Long,
  maxHeapSize: Long,
  currentParallelism: Int
)
