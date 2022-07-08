package org.jetbrains.plugins.scala
package lang
package completion
package filters
package expression

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ElseFilter extends ElementFilter {

  import ScalaCompletionUtil._

  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.is[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      var parent = leaf.getParent
      if (parent.is[ScExpression] && parent.getPrevSibling != null &&
          parent.getPrevSibling.getPrevSibling != null) {
        val ifStmt = parent.getPrevSibling match {
          case x: ScIf => x
          case x if x.is[PsiWhiteSpace] || x.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE =>
            x.getPrevSibling match {
              case x: ScIf => x
              case _ => null
            }
          case _ => null
        }
        var text = ""
        if (ifStmt == null) {
          while (parent != null && !parent.is[ScIf]) parent = parent.getParent
          if (parent == null) return false
          text = parent.getText
          text = replaceLiteral(text, " else true")
        } else {
          text = ifStmt.getText + " else true"
        }
        return checkElseWith(text, context = parent)
      }
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString: String = "else keyword filter"
}