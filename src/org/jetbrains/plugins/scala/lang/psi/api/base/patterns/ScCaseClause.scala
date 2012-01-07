package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import psi.ScalaPsiElement
import expr.{ScExpression, ScGuard}
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import lexer.ScalaTokenTypes

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScCaseClause extends ScalaPsiElement {
  def pattern = findChild(classOf[ScPattern])
  def expr = findChild(classOf[ScExpression])
  def guard = findChild(classOf[ScGuard])
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