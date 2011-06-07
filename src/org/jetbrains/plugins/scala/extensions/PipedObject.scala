package org.jetbrains.plugins.scala.extensions

/**
 * Pavel Fatin
 */

class PipedObject[T] (value:T) {
  def |>[R](f: T => R) = f(value)
}