package org.jetbrains.plugins.scala
package lang
package completion
package filters.toplevel

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiElement, _}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScStableReferenceElementPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.05.2008
 */

class TemplateFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val (leaf, _) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))
    
    if (leaf != null) {
      val parent = leaf.getParent
      val tuple = ScalaCompletionUtil.getForAll(parent, leaf)
      if (tuple._1) return tuple._2
      parent match {
        case _: ScReferenceExpression =>
          parent.getParent match {
            case y: ScStableReferenceElementPattern => {
              y.getParent match {
                case x: ScCaseClause => {
                  x.getParent.getParent match {
                    case _: ScMatch if (x.getParent.getFirstChild == x) => return false
                    case _: ScMatch => return true
                    case _ => return true
                  }
                }
                case _ =>
              }
            }
            case _ =>
          }
        case _ =>
      }
    }
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "template definitions keyword filter"
}