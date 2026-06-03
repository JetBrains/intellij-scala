package org.jetbrains.plugins.scala.extensions

object & {
  def unapply[T](obj: T): Some[(T, T)] = Some((obj, obj))
}