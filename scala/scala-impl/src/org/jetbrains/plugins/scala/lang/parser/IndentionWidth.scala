package org.jetbrains.plugins.scala
package lang
package parser

import IndentionWidth._

final class IndentionWidth(private val width: String) extends Ordered[IndentionWidth] {
  assert(width.forall(isIndentionChar))
  private val widthNum = width.count(_ == ' ') + width.count(_ == '\t') * 2

  override def compare(that: IndentionWidth): Int =
    this.widthNum compare that.widthNum

  override def toString: String = s"Indent[$widthNum]"
}

object IndentionWidth {
  val initial: IndentionWidth = new IndentionWidth("")

  private def isIndentionChar(c: Char): Boolean =
    c == ' ' || c == '\t'
  def apply(width: String): Option[IndentionWidth] =
    if (!width.forall(isIndentionChar)) None
    else Some(new IndentionWidth(width))
}