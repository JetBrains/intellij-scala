package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.dupLocator.util.DuplocatorUtil
import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.{MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScInfixExpr, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.structuralSearch.filter.{FunctionFilter, MatchingVariableFilter, MethodInvocationFilter}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}

class ScalaCompilingVisitor(globalVisitor: GlobalCompilingVisitor) extends ScalaRecursiveElementVisitor {

  def compile(topLevelElements: Array[PsiElement]): Unit = {
    globalVisitor.getContext.getPattern.setStrategy(new MatchingStrategy() {
      // Determine if we should match on this node
      // --> use it to only select nodes of the correct language
      override def continueMatching(start: PsiElement): Boolean =
        start.getLanguage match {
          case ScalaLanguage.INSTANCE | Scala3Language.INSTANCE => true
          case _ => false
        }

      override def shouldSkip(element: PsiElement, elementToMatchWith: PsiElement): Boolean =
        DuplocatorUtil.shouldSkip(element, elementToMatchWith)
    })

    val context = globalVisitor.getContext
    val pattern = context.getPattern
    for (element <- topLevelElements) {
      // activate this if we want to use the visitor
      element.accept(this)
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)))
    }
  }

  override def visitReference(ref: ScReference): Unit = {
    super.visitReference(ref)

    val pattern = globalVisitor.getContext.getPattern
    if (pattern.isRealTypedVar(ref)) {
      pattern.getHandler(ref) match {
        case substHand: SubstitutionHandler =>
          substHand.setFilter(new MatchingVariableFilter())
          substHand.setMatchHandler(new SymbolHandler(substHand))
        case _ =>
      }
    }
  }
//  override def visitReferenceExpression(ref: ScReferenceExpression): Unit = visitReference(ref)

  private class SymbolHandler(handler: SubstitutionHandler) extends MatchingHandler {
    override def `match`(patternNode: PsiElement, matchedNode: PsiElement, context: MatchContext): Boolean =
      handler.handle(matchedNode, context)
  }

  override def visitInfixExpression(infix: ScInfixExpr): Unit = visitMethodInvocation(infix)
  override def visitMethodCallExpression(call: ScMethodCall): Unit = visitMethodInvocation(call)

  def visitMethodInvocation(call: MethodInvocation): Unit = {
    super.visitExpression(call)

    globalVisitor
      .getContext.getPattern
      .getHandler(call).setFilter(new MethodInvocationFilter())
  }

  override def visitScalaElement(element: ScalaPsiElement): Unit = {
    globalVisitor.handle(element)
    super.visitScalaElement(element)
  }

  override def visitParameter(parameter: ScParameter): Unit = {
    super.visitParameter(parameter)

    val name = parameter.name
    val pattern = globalVisitor.getContext.getPattern
    if (pattern.isTypedVar(name)) {
      pattern.getHandler(name) match {
        case substHand: SubstitutionHandler =>
          substHand.setFilter(new MatchingVariableFilter())
          substHand.setMatchHandler(new SymbolHandler(substHand))
        case _ =>
      }
    }
  }

  override def visitFunction(fun: ScFunction): Unit = {
    super.visitFunction(fun)

    globalVisitor
      .getContext.getPattern
      .getHandler(fun).setFilter(new FunctionFilter())

    val name = fun.name
    val pattern = globalVisitor.getContext.getPattern
    if (pattern.isTypedVar(name)) {
      pattern.getHandler(name) match {
        case substHand: SubstitutionHandler =>
          substHand.setFilter(new MatchingVariableFilter())
          substHand.setMatchHandler(new SymbolHandler(substHand))
        case _ =>
      }
    }
  }

  // TODO could add filter to only match this pattern node to matching nodes, e.g. comment on comment
  // otherwise the default filter works by getClass comparison
}
