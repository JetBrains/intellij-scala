package org.jetbrains.plugins.scala

import scala.language.implicitConversions

/**
 * @author ven
 */
class Suspension[T](fun: () => T) {
  def this(t: T) = this ({() => t})

  lazy val v = fun()
}

object Suspension {
  implicit def any2Susp[T](t: T): Suspension[T] = new Suspension(t)
}