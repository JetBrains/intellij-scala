package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.compiler.CompilationUnitId

package object compilationCharts {

  type CompilationProgressState = Map[CompilationUnitId, CompilationProgressInfo]

  /**
   * Unix time in nano-seconds.
   */
  type Timestamp = Long

  /**
   * Memory in bytes
   */
  type Memory = Long
}
