package org.jetbrains.plugins.scala

object Misc {
  implicit def opt2bool(opt : Option[_]) = !opt.isEmpty

  implicit def fun2suspension[T](f : () => T) = new Suspension[T](f)
}