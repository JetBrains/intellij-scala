package org.jetbrains.plugins.scala

import scala.language.implicitConversions

object Misc {
  implicit def opt2bool(opt : Option[_]) = opt.isDefined

  implicit def fun2suspension[T](f : () => T) = new Suspension[T](f)
}
