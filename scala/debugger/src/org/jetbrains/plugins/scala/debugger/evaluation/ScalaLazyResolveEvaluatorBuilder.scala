package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.expression.{EvaluatorBuilder, EvaluatorBuilderImpl, ExpressionEvaluator, ExpressionEvaluatorImpl}
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.statistics.ScalaDebuggerUsagesCollector

private[debugger] object ScalaLazyResolveEvaluatorBuilder extends EvaluatorBuilder {
  override def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    if (codeFragment.getLanguage.is(JavaLanguage.INSTANCE))
      return EvaluatorBuilderImpl.getInstance().build(codeFragment, position) //java builder (e.g. SCL-6117)

    ScalaDebuggerUsagesCollector.logEvaluator(codeFragment.getProject)

    ExpressionEvaluatorImpl(ScalaLazyResolveEvaluator(scalaFragment(codeFragment), position))
  }

  /**
   * Like [[build]], but the evaluator interprets the expression itself and fails where [[build]] would compile it, for
   * callers that evaluate speculatively and must stay cheap.
   */
  def buildWithoutCompilation(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator =
    ExpressionEvaluatorImpl(ScalaLazyResolveEvaluator(scalaFragment(codeFragment), position, compileIfNeeded = false))

  private def scalaFragment(codeFragment: PsiElement): ScalaCodeFragment = codeFragment match {
    case sf: ScalaCodeFragment => sf
    case _ => throw IllegalArgumentException("Non-scala code fragment in scala evaluator builder")
  }
}
