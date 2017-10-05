package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.top.ClassTemplate

/**
  * @author adkozlov
  */
object SimpleExpr extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.SimpleExpr {
  override protected def argumentExprs = ArgumentExprs
  override protected def classTemplate = ClassTemplate
  override protected def literal = Literal
  override protected def blockExpr = BlockExpr
  override protected def expr = Expr
}
