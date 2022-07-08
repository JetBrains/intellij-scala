package org.jetbrains.plugins.scala.lang
package completion
package filters
package expression

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScDo, ScExpression}

class WhileFilter extends ElementFilter {

  import ScalaCompletionUtil._

  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      var parent = leaf.getParent
      if (parent.isInstanceOf[ScExpression] && parent.getPrevSibling != null &&
        parent.getPrevSibling.getPrevSibling != null) {
        val doStmt = parent.getPrevSibling match {
          case x: ScDo => x
          case x if x.isInstanceOf[PsiWhiteSpace] || x.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE =>
            x.getPrevSibling match {
              case x: ScDo => x
              case _ => null
            }
          case _ => null
        }
        var text = ""
        if (doStmt == null) {
          while (parent != null && !parent.isInstanceOf[ScDo]) parent = parent.getParent
          if (parent == null) return false
          text = parent.getText
          text = replaceLiteral(text, " while (true)")
        } else {
          text = doStmt.getText + " while (true)"
        }
        return checkDoWith(text, parent.getManager)
      }
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString: String = "'while' after 'do' keyword filter"
}
