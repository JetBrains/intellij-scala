package org.jetbrains.plugins.scala.debugger

import java.util
import java.util.Collections

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.{EvaluationContext, TextWithImports, TextWithImportsImpl}
import com.intellij.debugger.engine.{DebuggerUtils, FrameExtraVariablesProvider}
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.search.PsiSearchHelperImpl
import com.intellij.psi.search.{LocalSearchScope, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement, ResolveState}
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInsight.template.util.VariablesCompletionProcessor
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.{ScalaCompilingExpressionEvaluator, ScalaCompilingEvaluator}
import org.jetbrains.plugins.scala.debugger.evaluation.{EvaluationException, ScalaCodeFragmentFactory, ScalaEvaluatorBuilder, ScalaEvaluatorBuilderUtil}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.debugger.ui.ScalaParameterNameAdjuster
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

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

    val method = Try(evaluationContext.getFrameProxy.location().method()).toOption
    if (method.isEmpty || DebuggerUtils.isSynthetic(method.get) || ScalaSyntheticProvider.isMacroDefined(method.get))
      return Collections.emptySet()

    val element = inReadAction(sourcePosition.getElementAt)

    if (element == null) Collections.emptySet()
    else getVisibleVariables(element, evaluationContext, alreadyCollected).map(toTextWithImports).asJava
  }

  private def getVisibleVariables(elem: PsiElement, evaluationContext: EvaluationContext, alreadyCollected: util.Set[String]) = {
    val initialCandidates = inReadAction {
      val completionProcessor = new CollectingProcessor(elem)
      PsiTreeUtil.treeWalkUp(completionProcessor, elem, null, ResolveState.initial)
      completionProcessor.candidates
        .filter(srr => !alreadyCollected.asScala.map(ScalaParameterNameAdjuster.fixName).contains(srr.name))
        .filter(canEvaluate(_, elem))
    }
    val candidates = initialCandidates.filter(canEvaluateLong(_, elem, evaluationContext))
    val sorted = mutable.SortedSet()(Ordering.by[ScalaResolveResult, Int](_.getElement.getTextRange.getStartOffset))
    inReadAction {
      candidates.foreach(sorted += _)
    }
    sorted.map(_.name)
  }

  private def toTextWithImports(s: String) = {
    val xExpr = new XExpressionImpl(s, ScalaLanguage.Instance, "")
    TextWithImportsImpl.fromXExpression(xExpr)
  }

  private def canEvaluate(srr: ScalaResolveResult, place: PsiElement) = {
    srr.getElement match {
      case _: ScWildcardPattern => false
      case tp: ScTypedPattern if tp.name == "_" => false
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
      case named if ScalaEvaluatorBuilderUtil.isNotUsedEnumerator(named, place) => false
      case inNameContext(cc: ScCaseClause) if isInCatchBlock(cc) => false //cannot evaluate catched exceptions in scala
      case inNameContext(LazyVal(_)) => false //don't add lazy vals as they can be computed too early
      case _ => true
    }
  }

  private def canEvaluateLong(srr: ScalaResolveResult, place: PsiElement, evaluationContext: EvaluationContext) = {
    srr.getElement match {
      case named if generatorNotFromBody(named, place) => tryEvaluate(named.name, place, evaluationContext).isSuccess
      case named: PsiNamedElement if notUsedInCurrentClass(named, place) => tryEvaluate(named.name, place, evaluationContext).isSuccess
      case _ => true
    }
  }

  private def isInCatchBlock(cc: ScCaseClause): Boolean = {
    cc.parents.take(3).exists(_.isInstanceOf[ScCatchBlock])
  }

  private def tryEvaluate(name: String, place: PsiElement, evaluationContext: EvaluationContext): Try[AnyRef] = {
    Try {
      val evaluator = inReadAction {
        val twi = toTextWithImports(name)
        val codeFragment = new ScalaCodeFragmentFactory().createCodeFragment(twi, place, evaluationContext.getProject)
        val location = evaluationContext.getFrameProxy.location()
        val sourcePosition = ScalaPositionManager.instance(evaluationContext.getDebugProcess).map(_.getSourcePosition(location))
        if (sourcePosition.isEmpty) throw EvaluationException("Debug process is detached.")
        ScalaEvaluatorBuilder.build(codeFragment, sourcePosition.get) match {
          case _: ScalaCompilingExpressionEvaluator => throw EvaluationException("Don't use compiling evaluator here")
          case e => e
        }
      }
      evaluator.evaluate(evaluationContext)
    }
  }

  private def notUsedInCurrentClass(named: PsiNamedElement, place: PsiElement): Boolean = {
    inReadAction {
      val contextClass = ScalaEvaluatorBuilderUtil.getContextClass(place, strict = false)
      val containingClass = ScalaEvaluatorBuilderUtil.getContextClass(named)
      if (contextClass == containingClass) return false

      val placesToSearch = ArrayBuffer[PsiElement]()
      contextClass.accept(new ScalaRecursiveElementVisitor() {
        override def visitFunctionDefinition(fun: ScFunctionDefinition): Unit = {
          placesToSearch += fun
        }

        override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
          pat match {
            case LazyVal(_) => placesToSearch += pat
            case _ =>
          }
        }
      })
      if (placesToSearch.isEmpty) true
      else {
        val scopes = placesToSearch.map(new LocalSearchScope(_))
        val helper = new PsiSearchHelperImpl(place.getManager.asInstanceOf[PsiManagerEx])
        var used = false
        val processor = new TextOccurenceProcessor {
          override def execute(element: PsiElement, offsetInElement: Int): Boolean = {
            used = true
            false
          }
        }
        scopes.foreach { scope =>
          helper.processElementsWithWord(processor, scope, named.name, UsageSearchContext.IN_CODE, /*caseSensitive =*/ true)
        }
        !used
      }
    }
  }

  private def generatorNotFromBody(named: PsiNamedElement, place: PsiElement): Boolean = {
    inReadAction {
      val forStmt = ScalaPsiUtil.nameContext(named) match {
        case nc@(_: ScEnumerator | _: ScGenerator) =>
          Option(PsiTreeUtil.getParentOfType(nc, classOf[ScForStatement]))
        case _ => None
      }
      forStmt.flatMap(_.enumerators).exists(_.isAncestorOf(named)) && forStmt.flatMap(_.body).exists(!_.isAncestorOf(place))
    }
  }
}

private class CollectingProcessor(element: PsiElement)
                                 (implicit override val typeSystem: TypeSystem = element.getProject.typeSystem)
  extends VariablesCompletionProcessor(StdKinds.valuesRef) {

  val containingFile = element.getContainingFile
  val startOffset = element.getTextRange.getStartOffset
  val containingBlock = PsiTreeUtil.getParentOfType(element, classOf[ScBlock], classOf[ScTemplateDefinition], classOf[PsiFile])
  val usedNames: Set[String] =
    if (containingBlock != null) {
      containingBlock.depthFirst.collect {
        case ref: ScReferenceExpression if ref.qualifier.isEmpty => ref.refName
      }.toSet
    }
    else Set.empty

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val result = super.execute(element, state)

    candidatesSet.foreach(rr => if (!shouldShow(rr)) candidatesSet -= rr)
    result
  }

  private def shouldShow(candidate: ScalaResolveResult): Boolean = {
    val candElem = candidate.getElement
    val candElemContext = ScalaPsiUtil.nameContext(candElem) match {
      case cc: ScCaseClause => cc.pattern.getOrElse(cc)
      case other => other
    }
    def usedInContainingBlock = usedNames.contains(candElem.name)
    candElem.getContainingFile == containingFile && candElemContext.getTextRange.getEndOffset < startOffset && usedInContainingBlock
  }
}