package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiWhiteSpace}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFor, ScReferenceExpression}

class DoYieldFilter extends ElementFilter {
  override def isAcceptable(element: Any, context: PsiElement): Boolean = {
    if (!context.isInScala3File || context.is[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)

    if (leaf != null && leaf.getParent.is[ScReferenceExpression] && leaf.getParent.getParent != null) {
      val parent = leaf.getParent
      val errorBeforeDoYieldStart = parent.prevLeafs.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption()

      errorBeforeDoYieldStart match {
        case Some(err: PsiErrorElement) => err.getErrorDescription == ErrMsg("expected.do.or.yield")
        case _ =>
          parent.parentOfType[ScFor] match {
            case Some(scFor) => checkReplace(scFor, additionText = "do ()")
            case _ => false
          }
      }
    } else false
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "do, yield after for keyword filter"
}
