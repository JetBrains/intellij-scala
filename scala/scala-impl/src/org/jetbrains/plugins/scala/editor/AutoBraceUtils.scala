package org.jetbrains.plugins.scala
package editor

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}

object AutoBraceUtils {
  def nextExpressionInIndentationContext(element: PsiElement): Option[ScExpression] = {
    element.nextSibling match {
      case Some(e) => toIndentedExpression(e)
      case _ => None
    }
  }

  def previousExpressionInIndentationContext(element: PsiElement): Option[ScExpression] = {

    val orgStartOffset = element.endOffset
    val lastRealElement = PsiTreeUtil.prevLeaf(element)

    lastRealElement
      .withParents
      .takeWhile(e => !e.isInstanceOf[ScBlock] && e.endOffset <= orgStartOffset)
      .flatMap(toIndentedExpression)
      .headOption
  }


  private def toIndentedExpression(element: PsiElement): Option[ScExpression] = element match {
    case expr: ScExpression if AutoBraceUtils.isIndentationContext(element) && isPrecededByIndent(element) =>
      Some(expr)
    case _ =>
      None
  }

  private def isPrecededByIndent(element: PsiElement): Boolean = {
    element.getPrevSibling.nullSafe.exists(_.textContains('\n'))
  }

  def isIndentationContext(element: PsiElement): Boolean = {
    element.getParent match {
      case ScReturn(`element`) => true
      case ScIf(_, thenBranch, elseBranch) if thenBranch.contains(element) || elseBranch.contains(element) => true
      case ScWhile(_, Some(`element`)) => true
      case ScPatternDefinition.expr(`element`) => true
      case ScVariableDefinition.expr(`element`) => true
      case ScFunctionDefinition.withBody(`element`) => true
      case ScFor(_, `element`) => true
      case ScTry(Some(`element`), _, _) => true
      case ScFinallyBlock(`element`) => true
      case _ => false
    }
  }
}
