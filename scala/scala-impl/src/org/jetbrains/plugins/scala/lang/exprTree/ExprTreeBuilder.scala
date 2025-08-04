package org.jetbrains.plugins.scala.lang.exprTree

import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScUnderscoreSection}

import scala.collection.mutable

class ExprTreeBuilder {
  private var currentUnderscoresReversed = List.empty[UnderscoreInfo]

  private def newUnderscoreInfo(underscore: ScUnderscoreSection): UnderscoreInfo = {
    def test(i: Int): Int = 3
    val f = a => test(a)


    val i = currentUnderscoresReversed.size
    val info = UnderscoreInfo(underscore, i)
    currentUnderscoresReversed ::= info
    info
  }

  private def buildWithUnderscoreBounds(expr: ScExpression): ExprTree = expr match {
    case underscore: ScUnderscoreSection => build(expr)
    case _ =>
      val oldUnderscores = currentUnderscoresReversed
      currentUnderscoresReversed = Nil
      build(expr)
      if (currentUnderscoresReversed.nonEmpty) {
        val params = currentUnderscoresReversed.reverse.map()
      }
  }

  def build(expr: ScExpression): ExprTree = expr match {
    case interpolated: ScInterpolatedStringLiteral => ???
    case literal: ScLiteral => LiteralExprTree.fromPsi(literal)
    case underscore: ScUnderscoreSection =>
      val origin = newUnderscoreInfo(underscore)
      UnderscoreReferenceExprTree.Untyped(origin)
    case _ => ???
  }
}

object ExprTreeBuilder {
  def build(expr: ScExpression): ExprTree = {
    val builder = new ExprTreeBuilder
    builder.build(expr)
  }
}