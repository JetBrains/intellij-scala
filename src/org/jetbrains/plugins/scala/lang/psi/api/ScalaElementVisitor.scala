package org.jetbrains.plugins.scala.lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.{PsiFile, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariableDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author ilyas
 * @author Alexander Podkhalyuzin
 */

class ScalaRecursiveElementVisitor extends ScalaElementVisitor {
  override def visitElement(element: ScalaPsiElement): Unit = {
    element.acceptChildren(this)
  }
}

class ScalaElementVisitor extends PsiElementVisitor {

  override def visitFile(file: PsiFile) = file match {
    case sf: ScalaFile => visitElement(sf)
    case _ => visitElement(file)
  }

  def visitElement(element: ScalaPsiElement) = super.visitElement(element)
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


  // Expressions
  def visitExpression(expr: ScExpression) { visitElement(expr) }
  def visitReferenceExpression(ref: ScReferenceExpression) { visitReference(ref); visitExpression(ref) }
  def visitIfStatement(stmt: ScIfStmt) { visitExpression(stmt) }
  def visitAssignmentStatement(stmt: ScAssignStmt) { visitExpression(stmt) }
  def visitMethodCallExpression(call: ScMethodCall) { visitExpression(call) }
  def visitWhileStatement(ws: ScWhileStmt) { visitExpression(ws) }
  def visitReturnStatement(ret: ScReturnStmt) { visitExpression(ret) }
  def visitMatchStatement(ms: ScMatchStmt) { visitExpression(ms) }
  def visitForExpression(expr: ScForStatement) { visitExpression(expr) }
  def visitDoStatement(stmt: ScDoStmt) { visitExpression(stmt) }
  def visitFunctionExpression(stmt: ScFunctionExpr) { visitExpression(stmt) }
  def visitThrowExpression(throwStmt: ScThrowStmt) { visitExpression(throwStmt) }
  def visitTryExpression(tryStmt: ScTryStmt) { visitExpression(tryStmt) }
}