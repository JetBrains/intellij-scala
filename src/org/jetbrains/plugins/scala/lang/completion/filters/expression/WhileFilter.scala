package org.jetbrains.plugins.scala.lang.completion.filters.expression

import java.util.regex.{Matcher, Pattern}

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScDoStmt, ScExpression}

/**
 * @author Alefas
 * @since 23.03.12
 */
class WhileFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      var parent = leaf.getParent
      if (parent.isInstanceOf[ScExpression] && parent.getPrevSibling != null &&
        parent.getPrevSibling.getPrevSibling != null) {
        val doStmt = parent.getPrevSibling match {
          case x: ScDoStmt => x
          case x if x.isInstanceOf[PsiWhiteSpace] || x.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE =>
            x.getPrevSibling match {
              case x: ScDoStmt => x
              case _ => null
            }
          case _ => null
        }
        var text = ""
        if (doStmt == null) {
          while (parent != null && !parent.isInstanceOf[ScDoStmt]) parent = parent.getParent
          if (parent == null) return false
          text = parent.getText
          text = Pattern.compile(DUMMY_IDENTIFIER, Pattern.LITERAL).matcher(
            text).replaceAll(Matcher.quoteReplacement(" while (true)"))
        } else {
          text = doStmt.getText + " while (true)"
        }
        return checkDoWith(text, parent.getManager)
      }
    }
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "'while' after 'do' keyword filter"
  }
}
