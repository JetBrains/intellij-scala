package org.jetbrains.plugins.scala

object Misc {
  implicit def opt2bool[T] (opt : Option[T]) = !opt.isEmpty
}