package org.jetbrains.plugins.scala.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.{PsiFile, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern}
/**
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
  def visitReferenceExpression(ref: ScReferenceExpression) { visitReference(ref) }
  def visitIfStatement(stmt: ScIfStmt) { visitElement(stmt) }
  def visitAssignmentStatement(stmt: ScAssignStmt) { visitElement(stmt) }
  def visitMethodCallExpression(call: ScMethodCall) { visitElement(call) }
  def visitWhileStatement(ws: ScWhileStmt) { visitElement(ws) }
  def visitReturnStatement(ret: ScReturnStmt) { visitElement(ret) }
  def visitMatchStatement(ms: ScMatchStmt) { visitElement(ms) }
  def visitCaseClause(cc: ScCaseClause) { visitElement(cc) }
  def visitForExpression(expr: ScForStatement) { visitElement(expr) }
  def visitPattern(pat: ScPattern) { visitElement(pat) }
  def visitEnumerator(enum: ScEnumerator) { visitElement(enum) }
  def visitGenerator(gen: ScGenerator) { visitElement(gen) }
  def visitGuard(guard: ScGuard) { visitElement(guard) }
}