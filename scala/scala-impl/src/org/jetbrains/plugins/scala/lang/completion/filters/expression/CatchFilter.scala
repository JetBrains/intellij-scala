package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi._
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScArguments

class CatchFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.is[PsiComment]) return false
    val (leaf, _) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))
    if (leaf != null) {
      var i = getPrevNotWhitespaceAndComment(context.getTextRange.getStartOffset - 1, context)
      var leaf1 = getLeafByOffset(i, context)
      if (leaf1 == null || leaf1.getNode.getElementType == ScalaTokenTypes.kTRY) return false
      val prevIsRBrace = leaf1.textMatches("}")
      val prevIsRParan = leaf1.textMatches(")")
      while (leaf1 != null && !leaf1.is[ScTry]) {
        leaf1 match {
          case _: ScFinallyBlock =>
            return false
          case _: ScParenthesisedExpr | _: ScArguments if !prevIsRParan =>
            return false
          case _: ScBlock if !prevIsRBrace =>
            return false
          case _ =>
        }
        leaf1 = leaf1.getParent
      }
      if (leaf1 == null) return false
      //if (leaf1.getNode.getChildren(null).exists(_.getElementType == ScalaElementType.CATCH_BLOCK)) return false
      i = getNextNotWhitespaceAndComment(context.getTextRange.getEndOffset, context)
      if (leaf1.asInstanceOf[ScTry].catchBlock.isDefined) return false
      if (getLeafByOffset(i, context).textMatches("catch")) return false
      return true
    }

    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "statements keyword filter"
}