package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.engine.evaluation.{CodeFragmentKind, TextWithImports, TextWithImportsImpl}
import com.intellij.debugger.impl.EditorTextProvider
import com.intellij.openapi.util.{Pair, TextRange}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.SideEffectsUtil

import scala.util.Try

class ScalaEditorTextProvider extends EditorTextProvider {
  override def getEditorText(elementAtCaret: PsiElement): TextWithImports = {
    val result: String = findExpressionInner(elementAtCaret, allowMethodCalls = true).map(_.getText).getOrElse("")
    new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, result)
  }

  override def findExpression(element: PsiElement, allowMethodCalls: Boolean): Pair[PsiElement, TextRange] = {
    findExpressionInner(element, allowMethodCalls) match {
      case None => null
      case Some(elem) =>
        Try {
          val expressionCopy = ScalaPsiElementFactory.createExpressionWithContextFromText(elem.getText, elem.getContext, elem)
          new Pair[PsiElement, TextRange](expressionCopy, elem.getTextRange)
        }.toOption.orNull
    }
  }

  private def findExpressionInner(element: PsiElement, allowMethodCalls: Boolean): Option[PsiElement] = {
    def allowed(expr: ScExpression) = if (SideEffectsUtil.hasNoSideEffects(expr) || allowMethodCalls) Some(expr) else None

    PsiTreeUtil.getParentOfType(element, classOf[ScExpression], classOf[ScParameter], classOf[ScBindingPattern]) match {
      case (_: ScReferenceExpression) childOf (mc: ScMethodCall) => allowed(mc)
      case (ref: ScReferenceExpression) childOf (inf: ScInfixExpr) if inf.operation == ref => allowed(inf)
      case expr: ScExpression => allowed(expr)
      case b: ScBindingPattern => Some(b.nameId)
      case p: ScParameter if !p.isCallByNameParameter || allowMethodCalls => Some(p.nameId)
      case _ => None
    }
  }
}
