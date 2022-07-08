package org.jetbrains.plugins.scala
package extensions

object && {
  def unapply[T](obj: T): Some[(T, T)] = Some((obj, obj))
}