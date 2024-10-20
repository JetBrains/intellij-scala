package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.{PsiElementVisitor, PsiFile, PsiRecursiveVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScNamedTuplePattern, ScPattern, ScPatternArgumentList}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.{ScXmlEndTag, ScXmlStartTag}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScParameters, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScDerivesClause, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api._

class ScalaRecursiveElementVisitor extends ScalaElementVisitor with PsiRecursiveVisitor {
  //This stack was initially added in 2012 with commit message:
  //"Fixed exponent in recursive element visitor"
  //Not sure if it's actual anymore, but if we want to remove we need to make some benchmarks
  private var referencesStack = List.empty[ScReference]

  override def visitScalaElement(element: ScalaPsiElement): Unit = {
    if (referencesStack.nonEmpty && referencesStack.head == element) {
      referencesStack = null :: referencesStack.tail
    } else {
      element.acceptChildren(this)
    }
  }

  override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
    try {
      referencesStack = ref :: referencesStack
      visitReference(ref)
      visitExpression(ref)
    } finally {
      referencesStack = referencesStack.tail
    }
  }

  override def visitTypeProjection(proj: ScTypeProjection): Unit = {
    try {
      referencesStack = proj :: referencesStack
      visitReference(proj)
      visitTypeElement(proj)
    } finally {
      referencesStack = referencesStack.tail
    }
  }
}

// TODO
// The Visitor Pattern doesn't work well with there's an inheritance hierarchy.
// For example, because of the need to support recursion, visitReferenceExpression doesn't call visitReference or visitExpression, or visitScalaElement.
// This is inconsistent and misguiding. Even when ones knowns (and remembers) about that, one may need to replicate the inheritance hierarchy in each particular visitor.
abstract class ScalaElementVisitor extends PsiElementVisitor {
  def visitTypeAliasDefinition(alias: ScTypeAliasDefinition): Unit = visitTypeAlias(alias)

  def visitTypeAlias(alias: ScTypeAlias): Unit = visitScalaElement(alias)
  // TODO visitDeclaration

  def visitTypeAliasDeclaration(alias: ScTypeAliasDeclaration): Unit = visitTypeAlias(alias)

  def visitParameters(parameters: ScParameters): Unit = visitScalaElement(parameters)

  def visitParameterClause(clause: ScParameterClause): Unit = visitScalaElement(clause)

  def visitTypeParameterClause(clause: ScTypeParamClause): Unit = visitScalaElement(clause)

  def visitPatternArgumentList(args: ScPatternArgumentList): Unit = visitScalaElement(args)

  def visitArgumentExprList(args: ScArgumentExprList): Unit = visitScalaElement(args)

  def visitModifierList(modifierList: ScModifierList): Unit = visitScalaElement(modifierList)

  def visitConstructorInvocation(constrInvocation: ScConstructorInvocation): Unit = visitScalaElement(constrInvocation)

  def visitFunctionDefinition(fun: ScFunctionDefinition): Unit = visitFunction(fun)

  // TODO visitDeclaration
  def visitFunctionDeclaration(fun: ScFunctionDeclaration): Unit = visitFunction(fun)

  def visitMacroDefinition(fun: ScMacroDefinition): Unit = visitFunction(fun)

  def visitCatchBlock(c: ScCatchBlock): Unit = visitScalaElement(c)

  override def visitFile(file: PsiFile): Unit =
    file match {
      case sf: ScalaFile =>
        visitScalaElement(sf)
      case _ =>
        visitElement(file)
    }

  def visitScalaElement(element: ScalaPsiElement): Unit = super.visitElement(element)

  //Override also visitReferenceExpression! and visitTypeProjection!
  def visitReference(ref: ScReference): Unit = visitScalaElement(ref)
  def visitParameter(parameter: ScParameter): Unit = visitScalaElement(parameter)
  def visitClassParameter(parameter: ScClassParameter): Unit = visitParameter(parameter)
  def visitPatternDefinition(pat: ScPatternDefinition): Unit = visitValue(pat)
  // TODO visitDeclaration
  // TODO visitValueOrVariable
  def visitValueDeclaration(v: ScValueDeclaration): Unit = visitValue(v)
  def visitVariableDefinition(varr: ScVariableDefinition): Unit = visitVariable(varr)
  // TODO visitDeclaration
  // TODO visitValueOrVariable
  def visitVariableDeclaration(varr: ScVariableDeclaration): Unit = visitVariable(varr)
  // TODO visitValueOrVariable
  def visitVariable(varr: ScVariable): Unit = visitScalaElement(varr)
  // TODO visitValueOrVariable
  def visitValue(v: ScValue): Unit = visitScalaElement(v)
  def visitCaseClauses(ccs: ScCaseClauses): Unit = visitScalaElement(ccs)
  def visitCaseClause(cc: ScCaseClause): Unit = visitScalaElement(cc)
  def visitPattern(pat: ScPattern): Unit = visitScalaElement(pat)
  def visitForBinding(forBinding: ScForBinding): Unit = visitScalaElement(forBinding)
  def visitGenerator(gen: ScGenerator): Unit = visitScalaElement(gen)
  def visitGuard(guard: ScGuard): Unit = visitScalaElement(guard)
  def visitFunction(fun: ScFunction): Unit = visitScalaElement(fun)
  def visitTypeDefinition(typedef: ScTypeDefinition): Unit = visitScalaElement(typedef)
  def visitImportExpr(expr: ScImportExpr): Unit = visitScalaElement(expr)
  def visitSelfInvocation(self: ScSelfInvocation): Unit = visitScalaElement(self)
  def visitAnnotation(annotation: ScAnnotation): Unit = visitScalaElement(annotation)
  def visitTemplateParents(cp: ScTemplateParents): Unit = visitScalaElement(cp)
  def visitDerivesClause(td: ScDerivesClause): Unit = visitScalaElement(td)

  def visitEnumCases(cases: ScEnumCases): Unit = visitScalaElement(cases)

  // Expressions
  //Override also visitReferenceExpression!
  def visitExpression(expr: ScExpression): Unit = visitScalaElement(expr)
  def visitThisReference(t: ScThisReference): Unit = visitExpression(t)
  def visitSuperReference(t: ScSuperReference): Unit = visitExpression(t)
  // TODO visitReference
  // TODO visitExpression
  def visitReferenceExpression(ref: ScReferenceExpression): Unit = {}
  // TODO visitMethodInvocation
  def visitPostfixExpression(p: ScPostfixExpr): Unit = visitExpression(p)
  // TODO visitMethodInvocation
  def visitPrefixExpression(p: ScPrefixExpr): Unit = visitExpression(p)
  def visitIf(stmt: ScIf): Unit = visitExpression(stmt)
  def visitLiteral(l: ScLiteral): Unit = visitExpression(l)
  def visitAssignment(stmt: ScAssignment): Unit = visitExpression(stmt)
  // TODO visitMethodInvocation
  def visitMethodCallExpression(call: ScMethodCall): Unit = visitExpression(call)
  def visitGenericCallExpression(call: ScGenericCall): Unit = visitExpression(call)
  // TODO visitMethodInvocation
  def visitInfixExpression(infix: ScInfixExpr): Unit = visitExpression(infix)
  def visitWhile(ws: ScWhile): Unit = visitExpression(ws)
  def visitReturn(ret: ScReturn): Unit = visitExpression(ret)
  def visitMatch(ms: ScMatch): Unit = visitExpression(ms)
  def visitFor(expr: ScFor): Unit = visitExpression(expr)
  def visitDo(stmt: ScDo): Unit = visitExpression(stmt)
  def visitFunctionExpression(stmt: ScFunctionExpr): Unit = visitExpression(stmt)
  def visitPolyFunctionExpression(fun: ScPolyFunctionExpr): Unit = visitExpression(fun)
  def visitThrow(throwStmt: ScThrow): Unit = visitExpression(throwStmt)
  def visitTry(tryStmt: ScTry): Unit = visitExpression(tryStmt)
  def visitParenthesisedExpr(expr: ScParenthesisedExpr): Unit = visitExpression(expr)
  def visitNewTemplateDefinition(templ: ScNewTemplateDefinition): Unit = visitExpression(templ)
  def visitTypedExpr(stmt: ScTypedExpression): Unit = visitExpression(stmt)
  def visitTuple(tuple: ScTuple): Unit = visitExpression(tuple)
  def visitNamedTuple(tuple: ScNamedTuple): Unit = visitExpression(tuple)
  def visitNamedTuplePattern(pattern: ScNamedTuplePattern) = visitPattern(pattern)
  def visitBlockExpression(block: ScBlockExpr): Unit = visitExpression(block)
  def visitUnderscoreExpression(under: ScUnderscoreSection): Unit = visitExpression(under)
  def visitConstrBlockExpr(constr: ScConstrBlockExpr): Unit = visitBlockExpression(constr)

  //type elements
  //Override also visitTypeProjection!
  //If you use it for typed pattern, override visitTypeParam too.
  def visitTypeElement(te: ScTypeElement): Unit = visitScalaElement(te)
  def visitSimpleTypeElement(simple: ScSimpleTypeElement): Unit = visitTypeElement(simple)
  def visitWildcardTypeElement(wildcard: ScWildcardTypeElement): Unit = visitTypeElement(wildcard)
  // TODO visitReference
  def visitTypeProjection(proj: ScTypeProjection): Unit = {}
  def visitTupleTypeElement(tuple: ScTupleTypeElement): Unit = visitTypeElement(tuple)
  def visitNamedTupleTypeElement(tuple: ScNamedTupleTypeElement): Unit = visitTypeElement(tuple)
  def visitParenthesisedTypeElement(parenthesised: ScParenthesisedTypeElement): Unit = visitTypeElement(parenthesised)
  def visitParameterizedTypeElement(parameterized: ScParameterizedTypeElement): Unit = visitTypeElement(parameterized)
  def visitInfixTypeElement(infix: ScInfixTypeElement): Unit = visitTypeElement(infix)
  def visitFunctionalTypeElement(fun: ScFunctionalTypeElement): Unit = visitTypeElement(fun)
  def visitExistentialTypeElement(exist: ScExistentialTypeElement): Unit = visitTypeElement(exist)
  def visitCompoundTypeElement(compound: ScCompoundTypeElement): Unit = visitTypeElement(compound)
  def visitAnnotTypeElement(annot: ScAnnotTypeElement): Unit = visitTypeElement(annot)
  def visitTypeVariableTypeElement(tvar: ScTypeVariableTypeElement): Unit = visitTypeElement(tvar)
  def visitTypeLambdaTypeElement(lambda: ScTypeLambdaTypeElement): Unit = visitTypeElement(lambda)
  def visitContextBound(contextBound: ScContextBound): Unit = visitScalaElement(contextBound)

  //scaladoc
  // TODO visitScalaElement
  def visitDocComment(s: ScDocComment): Unit = visitComment(s)
  def visitScaladocElement(s: ScalaPsiElement): Unit = visitScalaElement(s)
  def visitWikiSyntax(s: ScDocSyntaxElement): Unit = visitScalaElement(s)
  def visitInlinedTag(s: ScDocInlinedTag): Unit = visitScalaElement(s)
  def visitTag(s: ScDocTag): Unit = visitScalaElement(s)

  //xml
  def visitXmlStartTag(s: ScXmlStartTag): Unit = visitScalaElement(s)
  def visitXmlEndTag(s: ScXmlEndTag): Unit = visitScalaElement(s)
}