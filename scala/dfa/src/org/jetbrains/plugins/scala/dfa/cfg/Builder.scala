package org.jetbrains.plugins.scala.dfa
package cfg

import org.jetbrains.plugins.scala.dfa.cfg.Builder.{Property, Variable}

trait Builder[Info] {
  type Value
  type UnlinkedJump
  type LoopLabel

  def constant(const: DfAny): Value

  def readVariable(variable: Variable): Unit
  def writeVariable(variable: Variable, value: Value): Unit

  def readProperty(base: Value, property: Property): Value
  def writeProperty(base: Value, property: Property, value: Value): Unit

  def jumpToFuture(): UnlinkedJump
  def jumpToFutureIfNot(cond: Value): UnlinkedJump
  def jumpHere(label: Seq[UnlinkedJump]): Unit

  final def jumpHere(first: UnlinkedJump, rest: UnlinkedJump*): Unit =
    jumpHere(first +: rest)

  def loopJumpHere(): LoopLabel
  def jumpBack(loop: LoopLabel): Unit

  def finish(): Graph[Info]
}

object Builder {
  case class Variable(anchor: Any)
  case class Property(anchor: Any)

  def newBuilder[Info](): Builder[Info] =
    new impl.BuilderImpl
}