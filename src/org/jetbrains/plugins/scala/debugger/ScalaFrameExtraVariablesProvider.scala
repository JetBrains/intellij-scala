package org.jetbrains.plugins.scala.debugger

import java.util

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.FrameExtraVariablesProvider
import com.intellij.debugger.engine.evaluation.{EvaluationContext, TextWithImports, TextWithImportsImpl}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState}
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInsight.template.util.VariablesCompletionProcessor
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
* Nikolay.Tropin
* 2014-12-04
*/
class ScalaFrameExtraVariablesProvider extends FrameExtraVariablesProvider {
  override def isAvailable(sourcePosition: SourcePosition, evaluationContext: EvaluationContext): Boolean = {
    ScalaDebuggerSettings.getInstance().SHOW_VARIABLES_FROM_OUTER_SCOPES &&
            sourcePosition.getFile.getLanguage == ScalaLanguage.Instance
  }

  override def collectVariables(sourcePosition: SourcePosition,
                                evaluationContext: EvaluationContext,
                                alreadyCollected: util.Set[String]): util.Set[TextWithImports] = {

    val result: mutable.SortedSet[String] = inReadAction {
      val element = sourcePosition.getElementAt
      if (element == null) mutable.SortedSet()
      else {
        getVisibleVariables(element).map(_.name).filter(!alreadyCollected.contains(_))
      }
    }
    result.map(toTextWithImports).asJava
  }

  private def getVisibleVariables(elem: PsiElement) = {
    val completionProcessor = new CollectingProcessor(elem)
    PsiTreeUtil.treeWalkUp(completionProcessor, elem, null, ResolveState.initial)
    val sorted = mutable.SortedSet()(Ordering.by[ScalaResolveResult, Int](_.getElement.getTextRange.getStartOffset))
    completionProcessor.candidates.filter(canEvaluate(_, elem)).foreach(sorted += _)
    sorted
  }

  private def toTextWithImports(s: String) = {
    val xExpr = new XExpressionImpl(s, ScalaLanguage.Instance, "")
    TextWithImportsImpl.fromXExpression(xExpr)
  }

  private def canEvaluate(srr: ScalaResolveResult, place: PsiElement) = {
    srr.getElement match {
      case cp: ScClassParameter if !cp.isEffectiveVal =>
        def notInThisClass(elem: PsiElement) = {
          elem != null && !PsiTreeUtil.isAncestor(cp.containingClass, elem, true)
        }
        val funDef = PsiTreeUtil.getParentOfType(place, classOf[ScFunctionDefinition])
        val lazyVal = PsiTreeUtil.getParentOfType(place, classOf[ScPatternDefinition]) match {
          case null => null
          case LazyVal(lzy) => lzy
          case _  => null
        }

        notInThisClass(funDef) || notInThisClass(lazyVal)
      case ScalaPsiUtil.inNameContext(LazyVal(_)) => false //don't add lazy vals as they can be computed too early
      case _ => true
    }
  }

}

private class CollectingProcessor(element: PsiElement) extends VariablesCompletionProcessor(StdKinds.valuesRef) {

  val containingFile = element.getContainingFile
  val startOffset = element.getTextRange.getStartOffset

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val result = super.execute(element, state)

    candidatesSet.foreach(rr => if (!isBeforeAndInSameFile(rr)) candidatesSet -= rr)
    result
  }

  private def isBeforeAndInSameFile(candidate: ScalaResolveResult): Boolean = {
    val candElem = candidate.getElement
    val candElemContext = ScalaPsiUtil.nameContext(candElem) match {
      case cc: ScCaseClause => cc.pattern.getOrElse(cc)
      case other => other
    }
    candElem.getContainingFile == containingFile && candElemContext.getTextRange.getEndOffset < startOffset
  }
}