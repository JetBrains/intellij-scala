package org.jetbrains.plugins.scala.util

/**
 * Regular ThreadLocal with added Scala syntactic sugar.
 */
private[scala] final class RichThreadLocal[T](init: => T) extends ThreadLocal[T] {
  override def initialValue(): T = init

  def value: T = get()

  def value_=(newValue: T): Unit = set(newValue)

  def withValue[R](newValue: T)(body: => R): R = {
    val old = value
    value = newValue
    try body
    finally value = old
  }
}
