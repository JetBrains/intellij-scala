package org.jetbrains.plugins.dotty.lang.parser.parsing.expressions

import org.jetbrains.plugins.dotty.lang.parser.parsing.top.ClassTemplate

/**
  * @author adkozlov
  */
object SimpleExpr extends org.jetbrains.plugins.scala.lang.parser.parsing.expressions.SimpleExpr {
  override protected val argumentExprs = ArgumentExprs
  override protected val classTemplate = ClassTemplate
  override protected val literal = Literal
  override protected val blockExpr = BlockExpr
  override protected val expr = Expr
}
