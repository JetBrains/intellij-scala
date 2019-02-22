package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.psi.{PsiElementVisitor, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.{ScXmlEndTag, ScXmlStartTag}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameters}
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

abstract class ScalaElementVisitor extends PsiElementVisitor {
  def visitTypeAliasDefinition(alias: ScTypeAliasDefinition) {visitTypeAlias(alias)}

  def visitTypeAlias(alias: ScTypeAlias) {visitScalaElement(alias)}

  def visitTypeAliasDeclaration(alias: ScTypeAliasDeclaration) {visitTypeAlias(alias)}

  def visitParameters(parameters: ScParameters) {visitScalaElement(parameters)}

  def visitModifierList(modifierList: ScModifierList) {visitScalaElement(modifierList)}

  def visitConstructorInvocation(constrInvocation: ScConstructorInvocation) {visitScalaElement(constrInvocation)}

  def visitFunctionDefinition(fun: ScFunctionDefinition) {visitFunction(fun)}

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
  def visitValueDeclaration(v: ScValueDeclaration) {visitValue(v)}
  def visitVariableDefinition(varr: ScVariableDefinition) { visitVariable(varr) }
  def visitVariableDeclaration(varr: ScVariableDeclaration) {visitVariable(varr) }
  def visitVariable(varr: ScVariable) {visitScalaElement(varr)}
  def visitValue(v: ScValue) {visitScalaElement(v)}
  def visitCaseClause(cc: ScCaseClause) { visitScalaElement(cc) }
  def visitPattern(pat: ScPattern) { visitScalaElement(pat) }
  def visitForBinding(forBinding: ScForBinding) { visitScalaElement(forBinding) }
  def visitGenerator(gen: ScGenerator) { visitScalaElement(gen) }
  def visitGuard(guard: ScGuard) { visitScalaElement(guard) }
  def visitFunction(fun: ScFunction) { visitScalaElement(fun) }
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
  // TODO isn't a reference expression a reference?
  // TODO isn't a reference expression an expression?
  def visitReferenceExpression(ref: ScReferenceExpression) {}
  def visitPostfixExpression(p: ScPostfixExpr) { visitExpression(p) }
  def visitPrefixExpression(p: ScPrefixExpr) { visitExpression(p) }
  def visitIfStatement(stmt: ScIf) { visitExpression(stmt) }
  def visitLiteral(l: ScLiteral) {visitExpression(l)}
  def visitAssignmentStatement(stmt: ScAssignment) { visitExpression(stmt) }
  def visitMethodCallExpression(call: ScMethodCall) { visitExpression(call) }
  def visitGenericCallExpression(call: ScGenericCall) { visitExpression(call) }
  def visitInfixExpression(infix: ScInfixExpr) {visitExpression(infix)}
  def visitWhileStatement(ws: ScWhile) { visitExpression(ws) }
  def visitReturnStatement(ret: ScReturn) { visitExpression(ret) }
  def visitMatchStatement(ms: ScMatch) { visitExpression(ms) }
  def visitForExpression(expr: ScFor) { visitExpression(expr) }
  def visitDoStatement(stmt: ScDo) { visitExpression(stmt) }
  def visitFunctionExpression(stmt: ScFunctionExpr) { visitExpression(stmt) }
  def visitThrowExpression(throwStmt: ScThrow) { visitExpression(throwStmt) }
  def visitTryExpression(tryStmt: ScTry) { visitExpression(tryStmt) }
  def visitExprInParent(expr: ScParenthesisedExpr) {visitExpression(expr)}
  def visitNewTemplateDefinition(templ: ScNewTemplateDefinition) {visitExpression(templ)}
  def visitTypedStmt(stmt: ScTypedExpression) {visitExpression(stmt)}
  def visitTupleExpr(tuple: ScTuple) {visitExpression(tuple)}
  def visitBlockExpression(block: ScBlockExpr) {visitExpression(block)}
  def visitUnderscoreExpression(under: ScUnderscoreSection) {visitExpression(under)}
  def visitConstrBlock(constr: ScConstrBlock) {visitBlockExpression(constr)}

  //type elements
  //Override also visitTypeProjection!
  //If you use it for typed pattern, override visitTypeParam too.
  def visitTypeElement(te: ScTypeElement) {visitScalaElement(te)}
  def visitSimpleTypeElement(simple: ScSimpleTypeElement) {visitTypeElement(simple)}
  def visitWildcardTypeElement(wildcard: ScWildcardTypeElement) {visitTypeElement(wildcard)}
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
  def visitDocComment(s: ScDocComment) {visitComment(s)}
  def visitScaladocElement(s: ScalaPsiElement) {visitScalaElement(s)}
  def visitWikiSyntax(s: ScDocSyntaxElement) {visitScalaElement(s)}
  def visitInlinedTag(s: ScDocInlinedTag) {visitScalaElement(s)}
  def visitTag(s: ScDocTag) {visitScalaElement(s)}

  //xml
  def visitXmlStartTag(s: ScXmlStartTag) {visitScalaElement(s)}
  def visitXmlEndTag(s: ScXmlEndTag) {visitScalaElement(s)}
}