package org.jetbrains.plugins.scala.extensions

/**
 * Pavel Fatin
 */

object Both {
  @deprecated("Use && instead")
  def unapply[T](obj: T) = Some((obj, obj))
}