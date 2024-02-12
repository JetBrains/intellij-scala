package org.jetbrains.plugins.scala.lang.completion.filters.other

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiWhiteSpace}
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.completion.filters.other.WithFilter.{checkWith, isBeforeWith}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGiven, ScTypeDefinition}

final class WithFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)
    if (
      // do not suggest if there is already a `with` keyword. e.g.:
      // class Test extends Base w<caret> with
      isBeforeWith(leaf) ||
      isAfterEmptyLine(leaf)) return false

    // class A extends B wi
    //                  ^ find error here
    // there will be no error if `with` is on new line
    val maybeErrorBeforeWithStart = leaf.prevLeafs
      .dropWhile(_.is[PsiComment, PsiWhiteSpace])
      .nextOption()

    maybeErrorBeforeWithStart match {
      case Some(error: PsiErrorElement) =>
        error.prevSibling
          .exists(checkWith)
      case Some(_) =>
        leaf.parentOfType[ScReference]
          .flatMap(_.prevSiblingNotWhitespaceComment)
          .exists(checkWith)
      case _ => false
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString: String = "'with' keyword filter"
}

object WithFilter {
  private def checkWith(element: PsiElement): Boolean =
    element match {
      case g: ScGiven =>
        checkGivenWith(g, "with { ??? }")
      case td: ScTypeDefinition =>
        checkClassWith(td, "with A", td.getManager)
      case td: ScNewTemplateDefinition =>
        checkNewWith(td, "with A", td.getManager)
      case te: ScTypeElement =>
        checkTypeWith(te, "with A", te.getManager)
      case _ => false
    }

  private def isBeforeWith(elem: PsiElement): Boolean =
    elem.nextVisibleLeaf(skipComments = true)
      .exists(_.elementType == ScalaTokenTypes.kWITH)
}
