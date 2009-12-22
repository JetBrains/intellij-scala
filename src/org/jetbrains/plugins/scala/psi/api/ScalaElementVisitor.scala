package org.jetbrains.plugins.scala.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.{PsiFile, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScCallExprImpl
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScAssignStmt, ScIfStmt, ScReferenceExpression}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaRecursiveElementVisitor extends ScalaElementVisitor {
  override def visitElement(element: ScalaPsiElement): Unit = {
    element.acceptChildren(this)
  }
}

class ScalaElementVisitor extends PsiElementVisitor {
  def visitReference(ref: ScReferenceElement) {
    visitElement(ref)
  }

  override def visitFile(file: PsiFile) = file match {
    case sf: ScalaFile => visitElement(sf)
    case _ => visitElement(file)
  }

  def visitElement(element: ScalaPsiElement) = super.visitElement(element)

  def visitPatternDefinition(pat: ScPatternDefinition) { visitElement(pat) }
  def visitVariableDefinition(varr: ScVariableDefinition) { visitElement(varr) }
  def visitReferenceExpression(ref: ScReferenceExpression) { visitElement(ref) }
  def visitIfStatement(stmt: ScIfStmt) { visitElement(stmt) }
  def visitAssignmentStatement(stmt: ScAssignStmt) { visitElement(stmt) }
  def visitMethodCallExpression(call: ScMethodCall) { visitElement(call) }
}