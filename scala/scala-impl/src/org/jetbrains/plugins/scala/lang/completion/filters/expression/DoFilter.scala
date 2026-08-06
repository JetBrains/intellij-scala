package org.jetbrains.plugins.scala.lang.completion.filters.expression

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiWhiteSpace}
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.filters.expression.DoFilter._
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScWhile}

class DoFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || !context.isInScala3File || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    if (leaf.getParent.is[ScReferenceExpression] && leaf.getParent.getParent != null) {
      val parent = leaf.getParent
      val grandParent = parent.getParent
      val errorAfterDoStart = grandParent.nextSiblings.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption()

      errorAfterDoStart match {
        case Some(err: PsiErrorElement) => checkErrorDescription(err)
        case _ if grandParent.is[ScWhile] =>
          val errorBeforeDoStart = parent.prevLeafs.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption()

          errorBeforeDoStart match {
            case Some(err: PsiErrorElement) => checkErrorDescription(err)
            case _ => false
          }
        case _ => false
      }
    } else false
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "do after while keyword filter"
}

object DoFilter {
  private def checkErrorDescription(err: PsiErrorElement): Boolean = err.getErrorDescription == ErrMsg("expected.do")
}
