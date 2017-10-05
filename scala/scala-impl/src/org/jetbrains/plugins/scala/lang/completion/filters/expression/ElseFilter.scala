package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import java.util.regex.{Matcher, Pattern}

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * @author Alexander Podkhalyuzin
 * @since 22.05.2008
 */
class ElseFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      var parent = leaf.getParent
      if (parent.isInstanceOf[ScExpression] && parent.getPrevSibling != null &&
          parent.getPrevSibling.getPrevSibling != null) {
        val ifStmt = parent.getPrevSibling match {
          case x: ScIfStmt => x
          case x if x.isInstanceOf[PsiWhiteSpace] || x.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE =>
            x.getPrevSibling match {
              case x: ScIfStmt => x
              case _ => null
            }
          case _ => null
        }
        var text = ""
        if (ifStmt == null) {
          while (parent != null && !parent.isInstanceOf[ScIfStmt]) parent = parent.getParent
          if (parent == null) return false
          text = parent.getText
          text = Pattern.compile(DUMMY_IDENTIFIER, Pattern.LITERAL).matcher(
            text).replaceAll(Matcher.quoteReplacement(" else true"))
        } else {
          text = ifStmt.getText + " else true"
        }
        return checkElseWith(text, parent.getManager)
      }
    }
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "statements keyword filter"
  }
}