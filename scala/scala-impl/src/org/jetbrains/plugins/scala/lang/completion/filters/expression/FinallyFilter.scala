package org.jetbrains.plugins.scala.lang.completion.filters.expression

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScArguments

class FinallyFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.isInstanceOf[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)
    if (leaf != null) {
      val prevCodeLeaf = PsiTreeUtil.prevCodeLeaf(context)

      if (prevCodeLeaf == null || prevCodeLeaf.getNode.getElementType == ScalaTokenTypes.kTRY)
        return false

      val prevCodeLeafNode = prevCodeLeaf.getNode
      val prevIsRBrace = prevCodeLeafNode.getElementType == ScalaTokenTypes.tRBRACE
      val prevIsRParan = prevCodeLeafNode.getElementType == ScalaTokenTypes.tRPARENTHESIS

      var curLeaf = prevCodeLeaf
      while (curLeaf != null && !curLeaf.isInstanceOf[ScTry]) {
        curLeaf match {
          case _: ScFinallyBlock =>
            return false
          case _: ScParenthesisedExpr | _: ScArguments if !prevIsRParan =>
            return false
          case _: ScBlock if !prevIsRBrace =>
            return false
          case _ =>
        }
        curLeaf = curLeaf.getParent
      }

      if (curLeaf == null)
        return false

      if (curLeaf.getNode.getChildren(null).exists(_.getElementType == ScalaElementType.FINALLY_BLOCK))
        return false

      val nextCodeLeaf = PsiTreeUtil.nextCodeLeaf(context)
      if (nextCodeLeaf != null) {
        nextCodeLeaf.getNode.getElementType match {
          case ScalaTokenTypes.kCATCH | ScalaTokenTypes.kFINALLY =>
            return false
          case _ =>
        }
      }
      true
    }
    else false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "statements keyword filter"
  }
}
