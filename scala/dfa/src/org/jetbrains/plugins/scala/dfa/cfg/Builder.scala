package org.jetbrains.plugins.scala.dfa
package cfg

import org.jetbrains.plugins.scala.dfa.cfg.Builder.{Property, Variable}

trait Builder[SourceInfo] {
  type Value
  type UnlinkedJump
  type LoopLabel

  def constant(const: DfAny): Value

  def readVariable(variable: Variable): Value
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

  def withSourceInfo[R](sourceInfo: SourceInfo)(body: => R): R

  def finish(): Graph[SourceInfo]
}

object Builder {
  case class Variable(anchor: Any)(override val toString: String)
  case class Property(anchor: Any)(override val toString: String)

  def newBuilder[Info](): Builder[Info] =
    new impl.BuilderImpl
}