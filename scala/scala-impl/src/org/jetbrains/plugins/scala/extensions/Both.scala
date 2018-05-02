package org.jetbrains.plugins.scala.extensions

/**
 * Pavel Fatin
 */

object Both {
  @deprecated("Use && instead", "2018.1")
  def unapply[T](obj: T) = Some((obj, obj))
}