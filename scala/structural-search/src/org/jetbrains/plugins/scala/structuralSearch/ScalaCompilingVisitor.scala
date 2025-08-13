package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.dupLocator.util.DuplocatorUtil
import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.{MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.structuralSearch.filter.{MethodInvocationFilter, ReferenceExpressionFilter}
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

  override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
    super.visitReferenceExpression(ref)
    val pattern = globalVisitor.getContext.getPattern
    if (pattern.isRealTypedVar(ref)) {
      pattern.getHandler(ref) match {
        case substHand: SubstitutionHandler =>
          substHand.setFilter(new ReferenceExpressionFilter())
          substHand.setMatchHandler(new SymbolHandler(substHand))
      }
    }
  }



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

  // TODO could add filter to only match this pattern node to matching nodes, e.g. comment on comment
  // otherwise the default filter works by getClass comparison
}
