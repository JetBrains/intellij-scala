package org.jetbrains.plugins.scala.debugger.evaluation


import com.intellij.debugger.engine.evaluation.expression.{ExpressionEvaluator, Evaluator, ExpressionEvaluatorImpl, EvaluatorBuilder}
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, TextWithImports}
import com.intellij.debugger.SourcePosition
import com.intellij.psi.PsiElement
import lang.psi.api.ScalaFile
import lang.psi.impl.ScalaPsiElementFactory
/**
 * User: Alexander Podkhalyuzin
 * Date: 06.03.2009
 */

class ScalaEvaluatorBuilder private extends EvaluatorBuilder {
  def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    Builder(position).buildElement(codeFragment)
  }

  def build(text: TextWithImports, contextElement: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    if (contextElement == null)
      throw EvaluateExceptionUtil.CANNOT_FIND_SOURCE_CLASS
    build(ScalaPsiElementFactory.createScalaFile(text.getImports + "\n" + text.getText, contextElement.getManager), position)
  }

  private case class Builder(position: SourcePosition) {
    var evaluator: Evaluator = null

    def buildElement(element: PsiElement): ExpressionEvaluator = {
      element match {
        case _: ScalaFile =>
        case _ =>
      }
      new ExpressionEvaluatorImpl(evaluator)
    }
  }
}

object ScalaEvaluatorBuilder {
  lazy val getInstance = new ScalaEvaluatorBuilder
}

