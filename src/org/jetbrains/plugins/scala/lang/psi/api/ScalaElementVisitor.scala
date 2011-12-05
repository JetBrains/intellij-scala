package org.jetbrains.plugins.scala.lang.psi.api

import base.types.ScSimpleTypeElement
import base.{ScLiteral, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.{PsiFile, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariableDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api._

/**
 * @author ilyas
 * @author Alexander Podkhalyuzin
 */

class ScalaRecursiveElementVisitor extends ScalaElementVisitor {
  override def visitElement(element: ScalaPsiElement) {
    element.acceptChildren(this)
  }
}

class ScalaElementVisitor extends PsiElementVisitor {

  override def visitFile(file: PsiFile) {
    file match {
      case sf: ScalaFile => visitElement(sf)
      case _ => visitElement(file)
    }
  }

  def visitElement(element: ScalaPsiElement) {super.visitElement(element)}
  def visitReference(ref: ScReferenceElement) { visitElement(ref) }
  def visitPatternDefinition(pat: ScPatternDefinition) { visitElement(pat) }
  def visitVariableDefinition(varr: ScVariableDefinition) { visitElement(varr) }
  def visitCaseClause(cc: ScCaseClause) { visitElement(cc) }
  def visitPattern(pat: ScPattern) { visitElement(pat) }
  def visitEnumerator(enum: ScEnumerator) { visitElement(enum) }
  def visitGenerator(gen: ScGenerator) { visitElement(gen) }
  def visitGuard(guard: ScGuard) { visitElement(guard) }
  def visitFunction(fun: ScFunction) { visitElement(fun) }
  def visitTypeDefintion(typedef: ScTypeDefinition) { visitElement(typedef) }
  def visitImportExpr(expr: ScImportExpr) {visitElement(expr)}


  // Expressions
  def visitExpression(expr: ScExpression) { visitElement(expr) }
  def visitThisReference(t: ScThisReference) {visitExpression(t)}
  def visitSuperReference(t: ScSuperReference) {visitExpression(t)}
  def visitReferenceExpression(ref: ScReferenceExpression) { visitReference(ref); visitExpression(ref) }
  def visitPostfixExpression(p: ScPostfixExpr) { visitExpression(p) }
  def visitPrefixExpression(p: ScPrefixExpr) { visitExpression(p) }
  def visitIfStatement(stmt: ScIfStmt) { visitExpression(stmt) }
  def visitLiteral(l: ScLiteral) {visitExpression(l)}
  def visitAssignmentStatement(stmt: ScAssignStmt) { visitExpression(stmt) }
  def visitMethodCallExpression(call: ScMethodCall) { visitExpression(call) }
  def visitGenericCallExpression(call: ScGenericCall) { visitExpression(call) }
  def visitInfixExpression(infix: ScInfixExpr) {visitExpression(infix)}
  def visitWhileStatement(ws: ScWhileStmt) { visitExpression(ws) }
  def visitReturnStatement(ret: ScReturnStmt) { visitExpression(ret) }
  def visitMatchStatement(ms: ScMatchStmt) { visitExpression(ms) }
  def visitForExpression(expr: ScForStatement) { visitExpression(expr) }
  def visitDoStatement(stmt: ScDoStmt) { visitExpression(stmt) }
  def visitFunctionExpression(stmt: ScFunctionExpr) { visitExpression(stmt) }
  def visitThrowExpression(throwStmt: ScThrowStmt) { visitExpression(throwStmt) }
  def visitTryExpression(tryStmt: ScTryStmt) { visitExpression(tryStmt) }
  def visitExprInParent(expr: ScParenthesisedExpr) {visitExpression(expr)}
  def visitNewTemplateDefinition(templ: ScNewTemplateDefinition) {visitExpression(templ)}
  def visitTypedStmt(stmt: ScTypedStmt) {visitExpression(stmt)}
  def visitTupleExpr(tuple: ScTuple) {visitExpression(tuple)}

  //type elements
  def visitSimpleTypeElement(simple: ScSimpleTypeElement) {visitElement(simple)}

  //scaladoc
  def visitDocComment(s: ScDocComment) {visitComment(s)}
  def visitScaladocElement(s: ScalaPsiElement) {visitElement(s)}
  def visitWikiSyntax(s: ScDocSyntaxElement) {visitElement(s)}
  def visitInlinedTag(s: ScDocInlinedTag) {visitElement(s)}
  def visitTag(s: ScDocTag) {visitElement(s)}
}