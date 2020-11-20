package org.jetbrains.plugins.scala.compilationCharts

case class CompilationProgressInfo(startTime: Timestamp,
                                   finishTime: Option[Timestamp],
                                   updateTime: Timestamp,
                                   progress: Double)