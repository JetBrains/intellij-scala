package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause

class IfFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      var parent = leaf.getParent
      while (parent != null) {
        if (parent.getNode.getElementType == ScalaElementType.FOR_STMT) {
          import org.jetbrains.plugins.scala.extensions._
          if (leaf.getParent != null && //reference
              leaf.getParent.getParent != null &&  //pattern
              leaf.getParent.getParent.getPrevSiblingNotWhitespace != null && //case keyword
              leaf.getParent.getParent.getPrevSiblingNotWhitespace.
                getNode.getElementType == ScalaTokenTypes.kCASE) return false
          return true
        }
        parent match {
          case clause: ScCaseClause =>
            if (clause.guard.isDefined) return false
            var position = clause.funType match {
              case Some(elem) => elem.getStartOffsetInParent
              case None => clause.getTextLength
            }
            val text = clause.getText
            while (text(position - 1).isWhitespace) position -= 1
            return leaf.getTextRange.getEndOffset == clause.getTextRange.getStartOffset + position
          case _ =>
        }
        parent = parent.getParent
      }
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "'if' keyword filter"
  }
}