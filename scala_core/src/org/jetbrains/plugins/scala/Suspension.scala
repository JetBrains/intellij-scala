package org.jetbrains.plugins.scala

/**
 * @author ven
 */
class Suspension[T](fun: () => T) {
  def this(t: T) = this ({() => t})

  lazy val v = fun()
}