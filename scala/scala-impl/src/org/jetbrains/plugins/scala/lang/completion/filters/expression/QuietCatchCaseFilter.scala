package org.jetbrains.plugins.scala.lang.completion.filters.expression

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiWhiteSpace}
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.filters.expression.QuietCatchCaseFilter._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScReferenceExpression, ScTry}

/** Also see [[org.jetbrains.plugins.scala.lang.completion.filters.modifiers.CaseFilter]] */
class QuietCatchCaseFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || !context.isInScala3File || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    if (leaf.getParent.is[ScReferenceExpression] && leaf.getParent.getParent != null) {
      leaf.getParent.getParent match {
        case catchBlock: ScCatchBlock if isWithoutParens(catchBlock) =>
          !leaf.nextLeafs
            .filterNot(_.is[PsiComment, PsiWhiteSpace])
            .nextOption()
            .exists(_.is[PsiErrorElement])
        case _ =>
          leaf.getParent.prevSiblings.filterNot(_.is[PsiComment, PsiWhiteSpace]).nextOption() match {
            case Some(scTry: ScTry) => checkTry(scTry)
            case Some(elem) =>
              elem.lastChild match {
                case Some(scTry: ScTry) => checkTry(scTry)
                case _ => false
              }
            case _ => false
          }
      }
    } else {
      false
    }
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "case in \"quiet\" catch syntax keyword filter"
}

object QuietCatchCaseFilter {
  private def isWithoutParens(catchBlock: ScCatchBlock): Boolean =
    catchBlock.getLeftParenthesis.isEmpty && catchBlock.getRightParenthesis.isEmpty

  private def checkTry(scTry: ScTry): Boolean = scTry.catchBlock match {
    case Some(catchBlock: ScCatchBlock) if isWithoutParens(catchBlock) =>
      catchBlock.expression.isEmpty && catchBlock.lastChild.exists(_.is[ScCaseClauses])
    case _ => false
  }
}
