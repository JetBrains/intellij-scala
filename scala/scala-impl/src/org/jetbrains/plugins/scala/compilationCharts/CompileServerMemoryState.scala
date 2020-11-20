package org.jetbrains.plugins.scala.compilationCharts

/**
 * @param maxHeapSize the maximum heap size in bytes
 * @param heapUsed used heap memory in a specific point of time
 */
final case class CompileServerMemoryState(maxHeapSize: Memory,
                                          heapUsed: Map[Timestamp, Memory])
