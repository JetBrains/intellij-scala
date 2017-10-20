package org.jetbrains.plugins.scala
package extensions

/**
 * Pavel Fatin
 */

object && {
  def unapply[T](obj: T): Option[(T, T)] = Some((obj, obj))
}