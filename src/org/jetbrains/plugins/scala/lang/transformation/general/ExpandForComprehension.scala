package org.jetbrains.plugins.scala.lang.transformation
package general

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.{&&, ElementText, FirstChild, Parent, PrevSibling, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, _}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
class ExpandForComprehension extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case e: ScForStatement => desugarRecursively(e)
  }

  private def desugarRecursively(e: PsiElement)(implicit project: Project) {
    e.depthFirst().toVector.reverse.foreach {
      case statement: ScForStatement => desugared(statement).foreach { expression =>
        clean(expression)
        desugarRecursively(statement.replace(expression))
      }
      case _ =>
    }
  }

  // TODO produce clean output in the getDesugarizedExprText itself (and return PsiElement, to avoid reparsing)
  private def desugared(e: ScForStatement)(implicit project: Project): Option[PsiElement] =
    e.getDesugarizedExprText(forDisplay = true)
      .map(parseElement(_))
      .map(e => {clean(e); e})

  // TODO use this clean output in the DesugarForIntention
  private def clean(e: PsiElement)(implicit project: Project) {
    e.depthFirst().toVector.reverse.foreach { it =>
      removeRedundantCaseClausesIn(it)
      removeRedundantParenthesesIn(it)
      convertRedundantBlockArgument(it)
      removeRedundantBracesIn(it)
    }
  }

  private def removeRedundantCaseClausesIn(e: PsiElement)(implicit project: Project) = Some(e) collect {
    case clauses @ ScCaseClauses(ScCaseClause(Some(p @ (_: ScReferencePattern | _: ScTypedPattern)), None, Some(ScBlock(expr)))) =>
      clauses.replace(code"(${p.getText}) => $expr")
  }

  private def removeRedundantParenthesesIn(e: PsiElement) = Some(e) collect {
    case it @ ScParenthesisedExpr(inner) if !ScalaPsiUtil.needParentheses(it, inner) => it.replace(inner)
    case it @ ScParenthesisedPattern(inner: ScReferencePattern) => it.replace(inner)
    case it @ ScParameterClause(inner) && FirstChild(ElementText("(")) if inner.typeElement.isEmpty =>
      inner.getFirstChild.delete()
      it.getFirstChild.delete()
      it.getLastChild.delete()
  }

  private def convertRedundantBlockArgument(e: PsiElement)(implicit project: Project) = Some(e) collect {
    case list @ ScArgumentExprList(ScBlockExpr.Expressions(expr)) =>
      list.replace(code"foo($expr)".getLastChild) match {
        case it @ PrevSibling(ws: PsiWhiteSpace) => ws.delete(); it
        case it => it
      }
  }

  private def removeRedundantBracesIn(e: PsiElement) = Some(e) collect {
    case it @ ScBlockExpr.Expressions(inner) && Parent(p) if !p.isInstanceOf[ScArgumentExprList] =>
      it.replace(inner)
  }
}
