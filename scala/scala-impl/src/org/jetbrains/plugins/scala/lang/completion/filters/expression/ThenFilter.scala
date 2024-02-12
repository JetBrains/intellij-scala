package org.jetbrains.plugins.scala.lang.completion.filters.expression

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiWhiteSpace}
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScIf, ScReferenceExpression}

class ThenFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    if (leaf.getParent.is[ScReferenceExpression] && leaf.getParent.getParent != null) {
      val grandParent = leaf.getParent.getParent
      val error = grandParent.nextSiblings.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption()

      error match {
        case Some(err: PsiErrorElement) =>
          err.getErrorDescription == ErrMsg("expected.then")
        case _ if grandParent.is[ScIf] =>
          checkThenWith(grandParent.getText + " then true else false", grandParent)
        case _ => false
      }
    } else {
      false
    }
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "then keyword filter"
}
