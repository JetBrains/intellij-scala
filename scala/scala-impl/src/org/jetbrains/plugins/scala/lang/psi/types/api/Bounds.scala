package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author adkozlov
  */
trait Bounds {
  def glb(first: ScType, second: ScType, checkWeak: Boolean = false): ScType

  def glb(types: collection.Seq[ScType], checkWeak: Boolean): ScType

  def lub(first: ScType, second: ScType, checkWeak: Boolean = true): ScType

  def lub(types: Iterable[ScType], checkWeak: Boolean): ScType
}
