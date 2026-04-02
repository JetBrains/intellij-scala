package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.{ConformanceContext, ScType}

trait Bounds {
  def glb(first: ScType, second: ScType, checkWeak: Boolean = false)(implicit context: ConformanceContext): ScType

  def lub(first: ScType, second: ScType, checkWeak: Boolean = true)(implicit context: ConformanceContext): ScType
}
