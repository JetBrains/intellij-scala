package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScExpression, ScGuard}

trait ScCaseClause extends ScalaPsiElement {
  def pattern: Option[ScPattern] = findChild[ScPattern]
  def expr: Option[ScExpression] = findChild[ScExpression]
  def guard: Option[ScGuard] = findChild[ScGuard]
  def funType: Option[PsiElement] = {
    val result = getNode.getChildren(TokenSet.create(ScalaTokenTypes.tFUNTYPE, 
      ScalaTokenTypes.tFUNTYPE_ASCII))
    if (result.length != 1) None
    else Some(result(0).getPsi)
  }
  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitCaseClause(this)
  }
}

object ScCaseClause {
  def unapply(e: ScCaseClause): Option[(Option[ScPattern], Option[ScGuard], Option[ScExpression])] =
    Option(e).map(e => (e.pattern, e.guard, e.expr))

  implicit class ScCaseClauseExt(private val cc: ScCaseClause) extends AnyVal {
    def resultExpr: Option[ScExpression] =
      cc.expr match {
        case Some(block: ScBlock) =>  block.resultExpression
        case _ => None
      }
  }
}