package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement


/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScIfStmt extends ScExpression {
  def condition: Option[ScExpression]
  def thenBranch : Option[ScExpression]
  def elseBranch : Option[ScExpression]
  def getLeftParenthesis : Option[PsiElement]
  def getRightParenthesis : Option[PsiElement]
  override def accept(visitor: ScalaElementVisitor) = visitor.visitIfStatement(this)
}

object ScIfStmt {
  def unapply(ifStmt: ScIfStmt) = Some(ifStmt.condition, ifStmt.thenBranch, ifStmt.elseBranch)
}