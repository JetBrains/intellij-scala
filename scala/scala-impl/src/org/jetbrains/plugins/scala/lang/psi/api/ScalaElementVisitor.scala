package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.psi.{PsiElementVisitor, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base._
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
  private val referencesStack = new mutable.Stack[ScReferenceElement]()
  
  override def visitElement(element: ScalaPsiElement) {
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

class ScalaElementVisitor extends PsiElementVisitor {
  def visitTypeAliasDefinition(alias: ScTypeAliasDefinition) {visitTypeAlias(alias)}

  def visitTypeAlias(alias: ScTypeAlias) {visitElement(alias)}

  def visitTypeAliasDeclaration(alias: ScTypeAliasDeclaration) {visitTypeAlias(alias)}

  def visitParameters(parameters: ScParameters) {visitElement(parameters)}

  def visitModifierList(modifierList: ScModifierList) {visitElement(modifierList)}

  def visitConstructor(constr: ScConstructor) {visitElement(constr)}

  def visitFunctionDefinition(fun: ScFunctionDefinition) {visitFunction(fun)}

  def visitFunctionDeclaration(fun: ScFunctionDeclaration) {visitFunction(fun)}

  def visitMacroDefinition(fun: ScMacroDefinition) {visitFunction(fun)}

  def visitCatchBlock(c: ScCatchBlock) {visitElement(c)}

  override def visitFile(file: PsiFile) {
    file match {
      case sf: ScalaFile => visitElement(sf)
      case _ => visitElement(file)
    }
  }

  def visitElement(element: ScalaPsiElement) {super.visitElement(element)}

  //Override also visitReferenceExpression! and visitTypeProjection!
  def visitReference(ref: ScReferenceElement) { visitElement(ref) }
  def visitParameter(parameter: ScParameter) {visitElement(parameter)}
  def visitClassParameter(parameter: ScClassParameter) {visitParameter(parameter)}
  def visitPatternDefinition(pat: ScPatternDefinition) { visitValue(pat) }
  def visitValueDeclaration(v: ScValueDeclaration) {visitValue(v)}
  def visitVariableDefinition(varr: ScVariableDefinition) { visitVariable(varr) }
  def visitVariableDeclaration(varr: ScVariableDeclaration) {visitVariable(varr) }
  def visitVariable(varr: ScVariable) {visitElement(varr)}
  def visitValue(v: ScValue) {visitElement(v)}
  def visitCaseClause(cc: ScCaseClause) { visitElement(cc) }
  def visitPattern(pat: ScPattern) { visitElement(pat) }
  def visitForBinding(forBinding: ScForBinding) { visitElement(forBinding) }
  def visitGenerator(gen: ScGenerator) { visitElement(gen) }
  def visitGuard(guard: ScGuard) { visitElement(guard) }
  def visitFunction(fun: ScFunction) { visitElement(fun) }
  def visitTypeDefinition(typedef: ScTypeDefinition) { visitElement(typedef) }
  def visitImportExpr(expr: ScImportExpr) {visitElement(expr)}
  def visitSelfInvocation(self: ScSelfInvocation) {visitElement(self)}
  def visitAnnotation(annotation: ScAnnotation) {visitElement(annotation)}
  def visitClass(cl: ScClass) {visitElement(cl)}
  def visitTemplateParents(cp: ScTemplateParents) {visitElement(cp)}


  // Expressions
  //Override also visitReferenceExpression!
  def visitExpression(expr: ScExpression) { visitElement(expr) }
  def visitThisReference(t: ScThisReference) {visitExpression(t)}
  def visitSuperReference(t: ScSuperReference) {visitExpression(t)}
  def visitReferenceExpression(ref: ScReferenceExpression) {} // TODO isn't a reference expression an expression?
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
  def visitTypeElement(te: ScTypeElement) {visitElement(te)}
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
  def visitScaladocElement(s: ScalaPsiElement) {visitElement(s)}
  def visitWikiSyntax(s: ScDocSyntaxElement) {visitElement(s)}
  def visitInlinedTag(s: ScDocInlinedTag) {visitElement(s)}
  def visitTag(s: ScDocTag) {visitElement(s)}

  //xml
  def visitXmlStartTag(s: ScXmlStartTag) {visitElement(s)}
  def visitXmlEndTag(s: ScXmlEndTag) {visitElement(s)}
}