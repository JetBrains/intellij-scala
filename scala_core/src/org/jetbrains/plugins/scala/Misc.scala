package org.jetbrains.plugins.scala

object Misc {
  implicit def opt2bool(opt : Option[_]) = !opt.isEmpty
}