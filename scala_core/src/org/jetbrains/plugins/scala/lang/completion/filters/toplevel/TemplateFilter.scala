package org.jetbrains.plugins.scala.lang.completion.filters.toplevel

import psi.api.base.patterns.{ScCaseClause, ScStableReferenceElementPattern, ScReferencePattern}
import psi.api.base.ScStableCodeReferenceElement
import com.intellij.lang.ASTNode
import psi.api.ScalaFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.05.2008
 */

class TemplateFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    var leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context);
    if (leaf != null && leaf.getContainingFile.asInstanceOf[ScalaFile].isScriptFile) leaf = leaf.getParent
    if (leaf != null) {
      val parent = leaf.getParent();
      val tuple = ScalaCompletionUtil.getForAll(parent, leaf)
      if (tuple._1) return tuple._2
      parent match {
        case _: ScReferenceExpression => {
          parent.getParent match {
            case y: ScStableReferenceElementPattern => {
              y.getParent match {
                case x: ScCaseClause => {
                  x.getParent.getParent match {
                    case _: ScMatchStmt if (x.getParent.getFirstChild == x) => return false
                    case _: ScMatchStmt => return true
                    case _ => return true
                  }
                }
                case _ =>
              }
            }
            case _ =>
          }
        }
        case _ =>
      }
    }
    return false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    return true
  }

  @NonNls
  override def toString(): String = {
    return "template definitions keyword filter";
  }
}