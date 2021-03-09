package org.jetbrains.plugins.scala.compilationCharts

case class CompilationProgressInfo(startTime: Timestamp,
                                   finishTime: Option[Timestamp],
                                   updateTime: Timestamp,
                                   progress: Double,
                                   phases: Seq[(Timestamp, String)],
                                   units: Seq[(Timestamp, String)])