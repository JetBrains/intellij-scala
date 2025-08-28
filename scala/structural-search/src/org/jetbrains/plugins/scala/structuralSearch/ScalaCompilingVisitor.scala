package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.dupLocator.util.DuplocatorUtil
import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.{MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScForBinding, ScInfixExpr, ScMatch, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScValueDeclaration, ScVariableDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.structuralSearch.filter.{CaseClauseFilter, FunctionFilter, MatchingVariableFilter, MethodInvocationFilter, TypeDefinitionFilter, TypeElementFilter, TypeParamFilter, ValueDeclarationFilter, VariableDeclarationFilter}
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
    placeVarHandler(ref.refName)
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
    element match {
      case _: ScReferencePattern => placeVarHandler(element.getText)
      case typeParam: ScTypeParam => visitTypeParam(typeParam)
      case _ =>
    }
    super.visitScalaElement(element)
  }

  override def visitParameter(parameter: ScParameter): Unit = {
    super.visitParameter(parameter)

    placeVarHandler(parameter.name)
  }

  override def visitCaseClause(cc: ScCaseClause): Unit = {
    super.visitCaseClause(cc)

    cc.pattern match {
      case Some(refPat: ScReferencePattern) =>
        placeVarHandler(refPat.getText)
      case _ =>
    }
    globalVisitor
      .getContext.getPattern
      .getHandler(cc).setFilter(new CaseClauseFilter())
  }

  override def visitFunction(fun: ScFunction): Unit = {
    super.visitFunction(fun)

    placeVarHandler(fun.name)
    globalVisitor
      .getContext.getPattern
      .getHandler(fun).setFilter(new FunctionFilter())
  }

  override def visitTypeElement(te: ScTypeElement): Unit = {
    super.visitTypeElement(te)
    placeVarHandler(te.getText)
    globalVisitor
      .getContext.getPattern
      .getHandler(te).setFilter(new TypeElementFilter())
  }

  def visitTypeParam(typeParam: ScTypeParam): Unit = {
    placeVarHandler(typeParam.getText)
    globalVisitor
      .getContext.getPattern
      .getHandler(typeParam).setFilter(new TypeParamFilter())
  }

  override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
    super.visitTypeDefinition(typedef)
    placeVarHandler(typedef.name)
    globalVisitor
      .getContext.getPattern
      .getHandler(typedef).setFilter(new TypeDefinitionFilter())
  }

  override def visitForBinding(forBinding: ScForBinding): Unit = {
    super.visitForBinding(forBinding)
    placeVarHandler(forBinding.getText)
  }

  override def visitAnnotation(annotation: ScAnnotation): Unit = {
    super.visitAnnotation(annotation)
    placeVarHandler(annotation.constructorInvocation.typeElement.getText)
  }

  override def visitConstructorInvocation(constrInvocation: ScConstructorInvocation): Unit = {
    super.visitConstructorInvocation(constrInvocation)
    placeVarHandler(constrInvocation.typeElement.getText)
  }

  override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
    super.visitPatternDefinition(pat)
    if (pat.declaredNames.size == 1)
      placeVarHandler(pat.declaredNames.head)
  }

  override def visitValueDeclaration(v: ScValueDeclaration): Unit = {
    super.visitValueDeclaration(v)
    if (v.declaredNames.size == 1)
      placeVarHandler(v.declaredNames.head)
    globalVisitor
      .getContext.getPattern
      .getHandler(v).setFilter(new ValueDeclarationFilter())
  }

  override def visitVariableDefinition(varr: ScVariableDefinition): Unit = {
    super.visitVariableDefinition(varr)
    if (varr.declaredNames.size == 1)
      placeVarHandler(varr.declaredNames.head)
  }

  override def visitVariableDeclaration(varr: ScVariableDeclaration): Unit = {
    super.visitVariableDeclaration(varr)
    if (varr.declaredNames.size == 1)
      placeVarHandler(varr.declaredNames.head)
    globalVisitor
      .getContext.getPattern
      .getHandler(varr).setFilter(new VariableDeclarationFilter())
  }

  private def placeVarHandler(name: String, setFilter: Boolean = true): Unit = {
    val pattern = globalVisitor.getContext.getPattern
    if (pattern.isTypedVar(name)) {
      pattern.getHandler(name) match {
        case substHand: SubstitutionHandler =>
          if (setFilter)
            substHand.setFilter(new MatchingVariableFilter())
          substHand.setMatchHandler(new MatchingHandler {
            override def `match`(patternNode: PsiElement, matchedNode: PsiElement, context: MatchContext): Boolean = {
              matchedNode.accept(ScalaCompilingVisitor.this)
              context.getMatcher.`match`(patternNode, matchedNode)
            }
          })
        case _ =>
      }
    }
  }

  // TODO could add filter to only match this pattern node to matching nodes, e.g. comment on comment
  // otherwise the default filter works by getClass comparison
}
