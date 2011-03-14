package org.jetbrains.plugins.scala.extensions.implementation

/**
 * Pavel Fatin
 */

class BooleanExt(b: Boolean) {
  def ifTrue[T](value: => T) = if (b) Some(value) else None
}
