package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.{EvaluationContext, TextWithImports, TextWithImportsImpl}
import com.intellij.debugger.engine.{DebuggerUtils, FrameExtraVariablesProvider}
import com.intellij.psi.impl.search.PsiSearchHelperImpl
import com.intellij.psi.search.{LocalSearchScope, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement, ResolveState}
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import org.jetbrains.plugins.scala.codeInsight.template.util.VariablesCompletionProcessor
import org.jetbrains.plugins.scala.debugger.evaluation.ReadActions.inNonBlockingReadAction
import org.jetbrains.plugins.scala.debugger.evaluation.{EvaluationException, ScalaCodeFragmentFactory, ScalaEvaluatorBuilderUtil, ScalaLazyResolveEvaluatorBuilder}
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.debugger.ui.ScalaParameterNameAdjuster
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState, StdKinds}
import org.jetbrains.plugins.scala.util.SideEffectsUtil
import org.jetbrains.plugins.scala.ScalaLanguage

import java.util
import java.util.Collections
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._
import scala.util.Try

class ScalaFrameExtraVariablesProvider extends FrameExtraVariablesProvider {
  override def isAvailable(sourcePosition: SourcePosition, evaluationContext: EvaluationContext): Boolean = {
    ScalaDebuggerSettings.getInstance().SHOW_VARIABLES_FROM_OUTER_SCOPES &&
      sourcePosition.getFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
  }

  override def collectVariables(sourcePosition: SourcePosition,
                                evaluationContext: EvaluationContext,
                                alreadyCollected: util.Set[String]): util.Set[TextWithImports] = {

    val method = Try(evaluationContext.getFrameProxy.location().method()).toOption
    if (method.isEmpty || DebuggerUtils.isSynthetic(method.get) || ScalaSyntheticProvider.isMacroDefined(method.get))
      return Collections.emptySet()

    val element = nonBlocking(evaluationContext)(sourcePosition.getElementAt)

    if (element == null) Collections.emptySet()
    else getVisibleVariables(element, evaluationContext, alreadyCollected).map(toTextWithImports).asJava
  }

  // Runs on the debugger thread for every frame shown; a plain read action here would make every write action (typing)
  // wait for the resolve below, a non-blocking one is cancelled and restarted instead
  private def nonBlocking[T](evaluationContext: EvaluationContext)(body: => T): T = {
    val process = evaluationContext.getDebugProcess
    inNonBlockingReadAction(expireWhen = () => !process.isAttached)(body)
  }

  private def getVisibleVariables(elem: PsiElement, evaluationContext: EvaluationContext, alreadyCollected: util.Set[String]): Set[String] = {
    val collectedNames = alreadyCollected.asScala.map(ScalaParameterNameAdjuster.fixName).toSet
    val initialCandidates = nonBlocking(evaluationContext) {
      val completionProcessor = new CollectingProcessor(elem)
      PsiTreeUtil.treeWalkUp(completionProcessor, elem, null, ScalaResolveState.empty)
      completionProcessor.candidates
        .filter(srr => !collectedNames.contains(srr.name))
        .filter(canEvaluate(_, elem))
    }
    val candidates = initialCandidates.filter(canEvaluateLong(_, elem, evaluationContext))
    nonBlocking(evaluationContext)(candidates.map(_.name).toSet)
  }

  private def toTextWithImports(s: String) = {
    val xExpr = new XExpressionImpl(s, ScalaLanguage.INSTANCE, "")
    TextWithImportsImpl.fromXExpression(xExpr)
  }

  @RequiresReadLock
  private def canEvaluate(srr: ScalaResolveResult, place: PsiElement): Boolean =
    srr.getElement match {
      case _: ScWildcardPattern => false
      case tp: ScTypedPattern if tp.name == "_" => false
      case cp: ScClassParameter if !cp.isClassMember =>
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
      case inNameContext(cc: ScCaseClause)  =>
        if (isInCatchBlock(cc)) false //cannot evaluate catched exceptions in scala
        else cc.getParent.getParent match {
          case m: ScMatch => m.expression.exists(SideEffectsUtil.mayOnlyThrow) //no side effects
          case _          => true
        }
      case inNameContext(LazyVal(_)) => false //don't add lazy vals as they can be computed too early
      case _ => true
    }

  // A value that is not certainly accessible from the frame is offered only if it can actually be evaluated there
  private def canEvaluateLong(srr: ScalaResolveResult, place: PsiElement, evaluationContext: EvaluationContext): Boolean = {
    val named = srr.getElement
    val nameToTry = nonBlocking(evaluationContext) {
      if (generatorNotFromBody(named, place) || notUsedInCurrentClass(named, place)) Some(named.name) else None
    }
    nameToTry.forall(name => tryEvaluate(name, place, evaluationContext).isSuccess)
  }

  private def tryEvaluate(name: String, place: PsiElement, evaluationContext: EvaluationContext): Try[AnyRef] = Try {
    val location = evaluationContext.getFrameProxy.location()
    val evaluator = nonBlocking(evaluationContext) {
      val twi = toTextWithImports(name)
      val codeFragment = new ScalaCodeFragmentFactory().createPsiCodeFragment(twi, place, evaluationContext.getProject)
      val sourcePosition = ScalaPositionManager.instance(evaluationContext.getDebugProcess).map(_.getSourcePosition(location))
      if (sourcePosition.isEmpty) throw EvaluationException(DebuggerBundle.message("debug.process.is.detached"))
      // a variable that can only be evaluated by compiling the expression is not worth the time it takes to find out
      ScalaLazyResolveEvaluatorBuilder.buildWithoutCompilation(codeFragment, sourcePosition.get)
    }
    evaluator.evaluate(evaluationContext)
  }

  @RequiresReadLock
  private def isInCatchBlock(cc: ScCaseClause): Boolean = {
    cc.parents.take(3).exists(_.isInstanceOf[ScCatchBlock])
  }


  @RequiresReadLock
  private def notUsedInCurrentClass(named: PsiNamedElement, place: PsiElement): Boolean = {
    val contextClass = ScalaEvaluatorBuilderUtil.getContextClass(place, strict = false)
    val containingClass = ScalaEvaluatorBuilderUtil.getContextClass(named)
    if (contextClass == containingClass) false
    else if (contextClass == null) true
    else {
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
        val helper = new PsiSearchHelperImpl(place.getProject)
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

  @RequiresReadLock
  private def generatorNotFromBody(named: PsiNamedElement, place: PsiElement): Boolean = {
    val forStmt = named.nameContext match {
      case nc@(_: ScForBinding | _: ScGenerator) =>
        Option(PsiTreeUtil.getParentOfType(nc, classOf[ScFor]))
      case _ => None
    }
    forStmt.flatMap(_.enumerators).exists(_.isAncestorOf(named)) && forStmt.flatMap(_.body).exists(!_.isAncestorOf(place))
  }
}

private class CollectingProcessor(element: PsiElement)
  extends VariablesCompletionProcessor(StdKinds.valuesRef)(using element) {

  private val containingFile = element.getContainingFile
  private val startOffset = element.getTextRange.getStartOffset
  private val containingBlock = PsiTreeUtil.getParentOfType(element, classOf[ScBlock], classOf[ScTemplateDefinition], classOf[PsiFile])
  private val usedNames: Set[String] =
    if (containingBlock != null) {
      containingBlock.depthFirst().collect {
        case ref: ScReferenceExpression if ref.qualifier.isEmpty => ref.refName
      }.toSet
    }
    else Set.empty

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    val result = super.execute(namedElement)
    candidatesSet.foreach(rr => if (!shouldShow(rr)) candidatesSet = candidatesSet - rr)
    result
  }

  private def shouldShow(candidate: ScalaResolveResult): Boolean = {
    val candElem = candidate.getElement
    val candElemContext = candElem.nameContext match {
      case cc: ScCaseClause => cc.pattern.getOrElse(cc)
      case other => other
    }
    def usedInContainingBlock = usedNames.contains(candElem.name)
    candElem.getContainingFile == containingFile && candElemContext.getTextRange.getEndOffset < startOffset && usedInContainingBlock
  }
}
