package org.jetbrains.plugins.scala.lang.parser

import org.jetbrains.plugins.scala.lang.parser.IndentationWidth._

final class IndentationWidth(private val width: String) extends Ordered[IndentationWidth] {
  private val widthNum = width.count(_ == ' ') + width.count(_ == '\t') * 2

  override def compare(that: IndentationWidth): Int =
    this.widthNum compare that.widthNum

  override def toString: String = s"Indent[$widthNum]"

  override def equals(obj: Any): Boolean = obj match {
    case other: IndentationWidth => other.widthNum == widthNum
    case _ => false
  }
}

object IndentationWidth {
  val initial: IndentationWidth = new IndentationWidth("")

  private def isIndentationChar(c: Char): Boolean =
    c == ' ' || c == '\t'
  def apply(width: String): Option[IndentationWidth] =
    if (!width.forall(isIndentationChar)) None
    else Some(new IndentationWidth(width))
}