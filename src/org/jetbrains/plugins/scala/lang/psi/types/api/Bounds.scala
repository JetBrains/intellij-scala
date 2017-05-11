package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author adkozlov
  */
trait Bounds {
  def glb(first: ScType, second: ScType, checkWeak: Boolean = false): ScType

  def glb(types: Seq[ScType], checkWeak: Boolean): ScType

  def lub(first: ScType, second: ScType, checkWeak: Boolean = true): ScType

  def lub(types: Seq[ScType], checkWeak: Boolean): ScType
}
