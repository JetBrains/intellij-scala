package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement


/** 
* @author Alexander Podkhalyuzin
*/

trait ScTryStmt extends ScExpression {
  def tryBlock = findChildByClassScala(classOf[ScTryBlock])
  def catchBlock = findChild(classOf[ScCatchBlock])
  def finallyBlock = findChild(classOf[ScFinallyBlock])

  override def accept(visitor: ScalaElementVisitor) = visitor.visitTryExpression(this)
}

object ScTryStmt {
  def unapply(tryStmt: ScTryStmt): Option[(ScTryBlock, Option[ScCatchBlock], Option[ScFinallyBlock])] =
    Some((tryStmt.tryBlock, tryStmt.catchBlock, tryStmt.finallyBlock))
}