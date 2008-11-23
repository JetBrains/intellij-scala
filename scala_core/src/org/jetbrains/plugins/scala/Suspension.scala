package org.jetbrains.plugins.scala

/**
 * @author ven
 */
class Suspension[T](fun: () => T) {
  def this(t: T) = this ({() => t})

  lazy val v = fun()

  override def hashCode = v.hashCode

  override def equals(p1: Any) = p1 match {
    case s : Suspension[T] => s.v == this.v
    case _ => false
  }
}