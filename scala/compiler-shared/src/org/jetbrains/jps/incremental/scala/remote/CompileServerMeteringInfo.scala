package org.jetbrains.jps.incremental.scala.remote

case class CompileServerMeteringInfo(maxParallelism: Int,
                                     maxHeapSizeMb: Int) // TODO replace with CompileServerMetrics
