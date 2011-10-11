package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluateRuntimeException, EvaluateException, TextWithImports}
import com.intellij.psi.{PsiFile, PsiElement}
import com.intellij.debugger.engine.evaluation.expression._
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
 * User: Alefas
 * Date: 11.10.11
 */

class ScalaEvaluatorBuilder extends EvaluatorBuilder {
  //todo: possibily will be removed
  def build(text: TextWithImports, contextElement: PsiElement, position: SourcePosition): ExpressionEvaluator = null

  def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    new Builder(position).buildElement(codeFragment)
  }
  
  private class Builder(position: SourcePosition) extends ScalaElementVisitor {
    private var myResult: Evaluator = null
    private var myCurrentFragmentEvaluator: CodeFragmentEvaluator = null

    override def visitFile(file: PsiFile) {
      if (!file.isInstanceOf[ScalaCodeFragment]) return
      val oldCurrentFragmentEvaluator = myCurrentFragmentEvaluator
      myCurrentFragmentEvaluator = new CodeFragmentEvaluator(oldCurrentFragmentEvaluator)
      val evaluators = new ArrayBuffer[Evaluator]()
      var child = file.getFirstChild
      while (child != null) {
        child.accept(this)
        if (myResult != null) {
          evaluators += myResult
        }
        myResult = null
        child = child.getNextSibling
      }
      myCurrentFragmentEvaluator.setStatements(evaluators.toArray)
      myResult = myCurrentFragmentEvaluator
      myCurrentFragmentEvaluator = oldCurrentFragmentEvaluator
    }

    override def visitReferenceExpression(ref: ScReferenceExpression) {

    }

    def buildElement(element: PsiElement): ExpressionEvaluator = {
      assert(element.isValid)
      try {
        element.accept(this)
      } catch {
        case e: EvaluateRuntimeException => throw e.getCause
      }
      if (myResult == null) {
        EvaluateExceptionUtil.createEvaluateException("Invalid evaluation expression")
      }
      new ExpressionEvaluatorImpl(myResult)
    }
  }
}