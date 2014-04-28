package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.impl.EditorTextProvider
import com.intellij.psi.PsiElement
import com.intellij.openapi.util.{TextRange, Pair}
import com.intellij.debugger.engine.evaluation.{CodeFragmentKind, TextWithImportsImpl, TextWithImports}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

/**
 * @author Alefas
 * @since 15.05.12
 */

class ScalaEditorTextProvider extends EditorTextProvider {
  def getEditorText(elementAtCaret: PsiElement): TextWithImports = {
    var result: String = ""
    val element: PsiElement = findExpressionInner(elementAtCaret, allowMethodCalls = true)
    if (element != null) result = element.getText
    new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, result)
  }

  def findExpression(element: PsiElement, allowMethodCalls: Boolean): Pair[PsiElement, TextRange] = {
    if (!allowMethodCalls) return null
    val expression: PsiElement = findExpressionInner(element, allowMethodCalls)
    if (expression == null) return null
    try {
      val expressionCopy = ScalaPsiElementFactory.createExpressionWithContextFromText(expression.getText,
        expression.getContext, expression)
      new Pair[PsiElement, TextRange](expressionCopy, expression.getTextRange)
    }
    catch {
      case t: Throwable => null
    }
  }

  private def findExpressionInner(element: PsiElement, allowMethodCalls: Boolean): PsiElement = {
    val parent: PsiElement = element.getParent
    parent match {
      case v: ScBindingPattern if element == v.nameId => element
      case p: ScParameter if element == p.nameId => element
      case ref: ScReferenceExpression =>
        ref.getParent match {
          case infix: MethodInvocation if infix.getInvokedExpr == ref =>
            if (allowMethodCalls) infix
            else null
          case _ => ref
        }
      case t: ScThisReference => parent
      case _ => null
    }
  }
}
