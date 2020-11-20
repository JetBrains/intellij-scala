package org.jetbrains.jps.incremental.scala.remote

/**
 * @param heapUsed used heap memory in bytes
 * @param maxHeapSize maximum heap size in bytes
 */
final case class CompileServerMetrics(heapUsed: Long,
                                      maxHeapSize: Long)