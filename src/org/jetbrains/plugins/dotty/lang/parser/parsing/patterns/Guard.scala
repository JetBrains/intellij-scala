package org.jetbrains.plugins.dotty.lang.parser.parsing.patterns

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.PostfixExpr

/**
  * @author adkozlov
  */
object Guard extends org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Guard {
  override protected val postfixExpr = PostfixExpr
}
