package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType}

trait Bounds {
  def glb(first: ScType, second: ScType, checkWeak: Boolean = false)(implicit context: Context): ScType

  def lub(first: ScType, second: ScType, checkWeak: Boolean = true)(implicit context: Context): ScType
}
