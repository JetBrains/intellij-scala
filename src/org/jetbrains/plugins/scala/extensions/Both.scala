package org.jetbrains.plugins.scala.extensions

/**
 * Pavel Fatin
 */

object Both {
  def unapply[T](obj: T) = Some((obj, obj))
}