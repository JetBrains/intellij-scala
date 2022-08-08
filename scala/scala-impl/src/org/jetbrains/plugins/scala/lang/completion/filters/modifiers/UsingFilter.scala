package org.jetbrains.plugins.scala.lang.completion.filters.modifiers

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiComment, PsiElement, PsiIdentifier, PsiWhiteSpace}
import org.jetbrains
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.filters.modifiers.UsingFilter._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

class UsingFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || !context.isInScala3File || context.is[PsiComment, PsiIdentifier]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    if (leaf != null) {
      leaf.getParent match {
        case param: ScParameter => isAfterLeftParen(param)
        case arg: ScReferenceExpression if arg.getParent.is[ScArgumentExprList] =>
          isAfterLeftParen(arg)
        case (_: ScStableCodeReference) && Parent(tpe: ScSimpleTypeElement) if isAfterLeftParen(tpe) =>
          isAfterGiven(tpe.getPrevSibling)
        case _ => false
      }
    } else false
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "using keyword filter"
}

object UsingFilter {
  private def isAfterGiven(elem: PsiElement): Boolean =
    elem.prevLeafs.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption() match {
      case Some(e) if e.getNode != null => e.getNode.getElementType == ScalaTokenType.GivenKeyword
      case _ => false
    }
}
