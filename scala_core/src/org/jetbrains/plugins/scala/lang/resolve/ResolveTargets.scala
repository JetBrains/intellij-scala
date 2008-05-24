package org.jetbrains.plugins.scala.lang.resolve

case class ResolveTargets(n: Int)

object ResolveTargets {
  val METHOD = new ResolveTargets(0)
  val VAR = new ResolveTargets(1)
  val VAL = new ResolveTargets(2)
  val OBJECT = new ResolveTargets(3)
  val CLASS = new ResolveTargets(4)
  val PACKAGE = new ResolveTargets(5)
}