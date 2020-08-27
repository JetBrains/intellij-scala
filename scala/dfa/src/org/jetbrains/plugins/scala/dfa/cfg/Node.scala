package org.jetbrains.plugins.scala.dfa
package cfg

sealed trait Node {
  type SourceInfo

  def graph: Graph[SourceInfo]
  def block: Block
  def sourceInfo: Option[SourceInfo]
  def index: Int

  def asmString(showIndex: Boolean = false, showLabel: Boolean = false, maxIndexHint: Int = 99): String
  def labelString: String
}

sealed trait Jumping extends Node {
  def targetIndex: Int
  final def target: Node = graph(targetIndex)
  final def targetLabel: String = target.labelString
}

sealed trait Value extends Node {
  def valueId: Int

  def valueIdString: String = "%" + valueId
}

trait End extends Node

trait Constant extends Value {
  def constant: DfAny
}

trait PhiValue extends Value {

}

trait Jump extends Jumping

trait JumpIfNot extends Jumping {
  def condition: Value
}

trait JumpTarget extends Value {

}