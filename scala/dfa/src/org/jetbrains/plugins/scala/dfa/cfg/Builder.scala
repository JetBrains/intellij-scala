package org.jetbrains.plugins.scala.dfa
package cfg

trait Builder[Info, V, P] {
  type Value
  type UnlinkedJump
  type LoopLabel

  def constant(const: DfAny): Value

  def readVariable(variable: V): Unit
  def writeVariable(variable: V, value: Value): Unit

  def readProperty(base: Value, property: P): Value
  def writeProperty(base: Value, property: P, value: Value): Unit

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
  def newBuilder[Info, V, P](): Builder[Info, V, P] =
    new impl.BuilderImpl
}