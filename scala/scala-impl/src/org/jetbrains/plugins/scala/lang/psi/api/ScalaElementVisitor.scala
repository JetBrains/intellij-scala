package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.psi.{PsiElementVisitor, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScPatternArgumentList}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.{ScXmlEndTag, ScXmlStartTag}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScParameters, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api._

import scala.collection.mutable

/**
 * @author ilyas
 * @author Alexander Podkhalyuzin
 */

class ScalaRecursiveElementVisitor extends ScalaElementVisitor {
  private val referencesStack = new mutable.Stack[ScReference]()
  
  override def visitScalaElement(element: ScalaPsiElement) {
    if (referencesStack.nonEmpty && referencesStack.top == element) {
      referencesStack.pop()
      referencesStack.push(null)
    } else {
      element.acceptChildren(this)
    }
  }

  override def visitReferenceExpression(ref: ScReferenceExpression) {
    try {
      referencesStack.push(ref)
      visitReference(ref)
      visitExpression(ref)
    } finally {
      referencesStack.pop()
    }
  }

  override def visitTypeProjection(proj: ScTypeProjection) {
    try {
      referencesStack.push(proj)
      visitReference(proj)
      visitTypeElement(proj)
    } finally {
      referencesStack.pop()
    }
  }
}

// TODO
// The Visitor Pattern doesn't work well with there's an inheritance hierarchy.
// For example, because of the need to support recursion, visitReferenceExpression doesn't call visitReference or visitExpression, or visitScalaElement.
// This is inconsistent and misguiding. Even when ones knowns (and remembers) about that, one may need to replicate the inheritance hierarchy in each particular visitor.
abstract class ScalaElementVisitor extends PsiElementVisitor {
  def visitTypeAliasDefinition(alias: ScTypeAliasDefinition) {visitTypeAlias(alias)}

  def visitTypeAlias(alias: ScTypeAlias) {visitScalaElement(alias)}

  // TODO visitDeclaration
  def visitTypeAliasDeclaration(alias: ScTypeAliasDeclaration) {visitTypeAlias(alias)}

  def visitParameters(parameters: ScParameters) {visitScalaElement(parameters)}

  def visitParameterClause(clause: ScParameterClause) {visitScalaElement(clause)}

  def visitTypeParameterClause(clause: ScTypeParamClause) {visitScalaElement(clause)}

  def visitPatternArgumentList(args: ScPatternArgumentList) {visitScalaElement(args)}

  def visitArgumentExprList(args: ScArgumentExprList) {visitScalaElement(args)}

  def visitModifierList(modifierList: ScModifierList) {visitScalaElement(modifierList)}

  def visitConstructorInvocation(constrInvocation: ScConstructorInvocation) {visitScalaElement(constrInvocation)}

  def visitFunctionDefinition(fun: ScFunctionDefinition) {visitFunction(fun)}

  // TODO visitDeclaration
  def visitFunctionDeclaration(fun: ScFunctionDeclaration) {visitFunction(fun)}

  def visitMacroDefinition(fun: ScMacroDefinition) {visitFunction(fun)}

  def visitCatchBlock(c: ScCatchBlock) {visitScalaElement(c)}

  override def visitFile(file: PsiFile) {
    file match {
      case sf: ScalaFile => visitScalaElement(sf)
      case _ => visitElement(file)
    }
  }

  def visitScalaElement(element: ScalaPsiElement) {super.visitElement(element)}

  //Override also visitReferenceExpression! and visitTypeProjection!
  def visitReference(ref: ScReference) { visitScalaElement(ref) }
  def visitParameter(parameter: ScParameter) {visitScalaElement(parameter)}
  def visitClassParameter(parameter: ScClassParameter) {visitParameter(parameter)}
  def visitPatternDefinition(pat: ScPatternDefinition) { visitValue(pat) }
  // TODO visitDeclaration
  // TODO visitValueOrVariable
  def visitValueDeclaration(v: ScValueDeclaration) {visitValue(v)}
  def visitVariableDefinition(varr: ScVariableDefinition) { visitVariable(varr) }
  // TODO visitDeclaration
  // TODO visitValueOrVariable
  def visitVariableDeclaration(varr: ScVariableDeclaration) {visitVariable(varr) }
  // TODO visitValueOrVariable
  def visitVariable(varr: ScVariable) {visitScalaElement(varr)}
  // TODO visitValueOrVariable
  def visitValue(v: ScValue) {visitScalaElement(v)}
  def visitCaseClause(cc: ScCaseClause) { visitScalaElement(cc) }
  def visitPattern(pat: ScPattern) { visitScalaElement(pat) }
  def visitForBinding(forBinding: ScForBinding) { visitScalaElement(forBinding) }
  def visitGenerator(gen: ScGenerator) { visitScalaElement(gen) }
  def visitGuard(guard: ScGuard) { visitScalaElement(guard) }
  def visitFunction(fun: ScFunction) { visitScalaElement(fun) }

  def visitEnumCase(enumCase: ScEnumCase): Unit = {
    visitScalaElement(enumCase)
  }
  def visitTypeDefinition(typedef: ScTypeDefinition) { visitScalaElement(typedef) }
  def visitImportExpr(expr: ScImportExpr) {visitScalaElement(expr)}
  def visitSelfInvocation(self: ScSelfInvocation) {visitScalaElement(self)}
  def visitAnnotation(annotation: ScAnnotation) {visitScalaElement(annotation)}
  def visitClass(cl: ScClass) {visitScalaElement(cl)}
  def visitTemplateParents(cp: ScTemplateParents) {visitScalaElement(cp)}


  // Expressions
  //Override also visitReferenceExpression!
  def visitExpression(expr: ScExpression) { visitScalaElement(expr) }
  def visitThisReference(t: ScThisReference) {visitExpression(t)}
  def visitSuperReference(t: ScSuperReference) {visitExpression(t)}
  // TODO visitReference
  // TODO visitExpression
  def visitReferenceExpression(ref: ScReferenceExpression) {}
  // TODO visitMethodInvocation
  def visitPostfixExpression(p: ScPostfixExpr) { visitExpression(p) }
  // TODO visitMethodInvocation
  def visitPrefixExpression(p: ScPrefixExpr) { visitExpression(p) }
  def visitIf(stmt: ScIf) { visitExpression(stmt) }
  def visitLiteral(l: ScLiteral) {visitExpression(l)}
  def visitAssignment(stmt: ScAssignment) { visitExpression(stmt) }
  // TODO visitMethodInvocation
  def visitMethodCallExpression(call: ScMethodCall) { visitExpression(call) }
  def visitGenericCallExpression(call: ScGenericCall) { visitExpression(call) }
  // TODO visitMethodInvocation
  def visitInfixExpression(infix: ScInfixExpr) {visitExpression(infix)}
  def visitWhile(ws: ScWhile) { visitExpression(ws) }
  def visitReturn(ret: ScReturn) { visitExpression(ret) }
  def visitMatch(ms: ScMatch) { visitExpression(ms) }
  def visitFor(expr: ScFor) { visitExpression(expr) }
  def visitDo(stmt: ScDo) { visitExpression(stmt) }
  def visitFunctionExpression(stmt: ScFunctionExpr) { visitExpression(stmt) }
  def visitThrow(throwStmt: ScThrow) { visitExpression(throwStmt) }
  def visitTry(tryStmt: ScTry) { visitExpression(tryStmt) }
  def visitParenthesisedExpr(expr: ScParenthesisedExpr) {visitExpression(expr)}
  def visitNewTemplateDefinition(templ: ScNewTemplateDefinition) {visitExpression(templ)}
  def visitTypedStmt(stmt: ScTypedExpression) {visitExpression(stmt)}
  def visitTuple(tuple: ScTuple) {visitExpression(tuple)}
  def visitBlockExpression(block: ScBlockExpr) {visitExpression(block)}
  def visitUnderscoreExpression(under: ScUnderscoreSection) {visitExpression(under)}
  def visitConstrBlock(constr: ScConstrBlock) {visitBlockExpression(constr)}

  //type elements
  //Override also visitTypeProjection!
  //If you use it for typed pattern, override visitTypeParam too.
  def visitTypeElement(te: ScTypeElement) {visitScalaElement(te)}
  def visitSimpleTypeElement(simple: ScSimpleTypeElement) {visitTypeElement(simple)}
  def visitWildcardTypeElement(wildcard: ScWildcardTypeElement) {visitTypeElement(wildcard)}
  // TODO visitReference
  def visitTypeProjection(proj: ScTypeProjection) {}
  def visitTupleTypeElement(tuple: ScTupleTypeElement) {visitTypeElement(tuple)}
  def visitParenthesisedTypeElement(parenthesised: ScParenthesisedTypeElement) {visitTypeElement(parenthesised)}
  def visitParameterizedTypeElement(parameterized: ScParameterizedTypeElement) {visitTypeElement(parameterized)}
  def visitInfixTypeElement(infix: ScInfixTypeElement) {visitTypeElement(infix)}
  def visitFunctionalTypeElement(fun: ScFunctionalTypeElement) {visitTypeElement(fun)}
  def visitExistentialTypeElement(exist: ScExistentialTypeElement) {visitTypeElement(exist)}
  def visitCompoundTypeElement(compound: ScCompoundTypeElement) {visitTypeElement(compound)}
  def visitAnnotTypeElement(annot: ScAnnotTypeElement) {visitTypeElement(annot)}
  def visitTypeVariableTypeElement(tvar: ScTypeVariableTypeElement): Unit = { visitTypeElement(tvar) }

  //scaladoc
  // TODO visitScalaElement
  def visitDocComment(s: ScDocComment) {visitComment(s)}
  def visitScaladocElement(s: ScalaPsiElement) {visitScalaElement(s)}
  def visitWikiSyntax(s: ScDocSyntaxElement) {visitScalaElement(s)}
  def visitInlinedTag(s: ScDocInlinedTag) {visitScalaElement(s)}
  def visitTag(s: ScDocTag) {visitScalaElement(s)}

  //xml
  def visitXmlStartTag(s: ScXmlStartTag) {visitScalaElement(s)}
  def visitXmlEndTag(s: ScXmlEndTag) {visitScalaElement(s)}
}