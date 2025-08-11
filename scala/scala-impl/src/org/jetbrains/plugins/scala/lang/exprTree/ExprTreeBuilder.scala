package org.jetbrains.plugins.scala.lang.exprTree

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure

import scala.collection.mutable

class ExprTreeBuilder(rootExpr: PsiElement) {
  private implicit val elementScope: ElementScope = ElementScope(rootExpr)

  private var currentUnderscoresReversed = List.empty[UnderscoreInfo]

  private def newUnderscoreInfo(underscore: ScUnderscoreSection): UnderscoreInfo = {
    def test(i: Int): Int = 3
    val f = a => test(a)


    val i = currentUnderscoresReversed.size
    val info = UnderscoreInfo(underscore, i)
    currentUnderscoresReversed ::= info
    info
  }

  private def buildWithUnderscoreBounds(expr: ScExpression, hasParent: Boolean = true): ExprTree = expr match {
    case underscore: ScUnderscoreSection if hasParent => build(expr)
    case _ =>
      val oldUnderscores = currentUnderscoresReversed
      currentUnderscoresReversed = Nil
      try {
        val body = build(expr)
        if (currentUnderscoresReversed.nonEmpty) {
          val params = currentUnderscoresReversed.reverse
          FunctionLiteralExprTree.fromUnderscores(params, body)
        } else {
          body
        }
      } finally {
        currentUnderscoresReversed = oldUnderscores
      }
  }

  def build(expr: ScExpression): ExprTree = expr match {
    case interpolated: ScInterpolatedStringLiteral => ???
    case literal: ScLiteral => LiteralExprTree.fromPsi(literal)
    case underscore: ScUnderscoreSection =>
      val origin = newUnderscoreInfo(underscore)
      UnderscoreReferenceExprTree.Untyped(origin)
    case fun: ScFunctionExpr =>
      val bodyTree = useOrError(fun.result, expr)(buildWithUnderscoreBounds(_))
      FunctionLiteralExprTree.fromPsi(fun, bodyTree)
    case _ =>
      ???
  }

  private def useOrError[T <: PsiElement](psi: Option[T], parent: PsiElement)(f: T => ExprTree): ExprTree = {
    psi match {
      case Some(psi) => f(psi)
      case None =>
        ErrorExprTree(
          new Failure(NlsString.force("error")),
          ErrorExprTree.Origin.ParentElement(parent)
        )
    }
  }
}

object ExprTreeBuilder {
  def build(expr: ScExpression): ExprTree = {
    val builder = new ExprTreeBuilder(expr)
    builder.buildWithUnderscoreBounds(expr, hasParent = false)
  }
}