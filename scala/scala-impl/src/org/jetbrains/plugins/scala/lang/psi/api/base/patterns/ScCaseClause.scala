package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScGuard}

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScCaseClause extends ScalaPsiElement {
  def pattern: Option[ScPattern] = findChild(classOf[ScPattern])
  def expr: Option[ScExpression] = findChild(classOf[ScExpression])
  def guard: Option[ScGuard] = findChild(classOf[ScGuard])
  def funType: Option[PsiElement] = {
    val result = getNode.getChildren(TokenSet.create(ScalaTokenTypes.tFUNTYPE, 
      ScalaTokenTypes.tFUNTYPE_ASCII))
    if (result.length != 1) None
    else Some(result(0).getPsi)
  }
  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitCaseClause(this)
  }
}

object ScCaseClause {
  def unapply(e: ScCaseClause): Option[(Option[ScPattern], Option[ScGuard], Option[ScExpression])] =
    Option(e).map(e => (e.pattern, e.guard, e.expr))
}