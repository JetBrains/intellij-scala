package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.Bounds
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScIfStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScIfStmt {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "IfStatement"

  def condition = {
    val rpar = findChildByType(ScalaTokenTypes.tRPARENTHESIS)
    val c = if (rpar != null) PsiTreeUtil.getPrevSiblingOfType(rpar, classOf[ScExpression]) else null
    if (c == null) None else Some(c)
  }

  def thenBranch = {
    val kElse = findChildByType(ScalaTokenTypes.kELSE)
    val t =
      if (kElse != null) PsiTreeUtil.getPrevSiblingOfType(kElse, classOf[ScExpression])
      else getLastChild match {
        case expression: ScExpression => expression
        case _ => PsiTreeUtil.getPrevSiblingOfType(getLastChild, classOf[ScExpression])
    }
    if (t == null) None else condition match {
      case None => Some(t) 
      case Some(c) if c != t => Some(t)
      case  _ => None
    }
  }

  def elseBranch = {
    val kElse = findChildByType(ScalaTokenTypes.kELSE)
    val e = if (kElse != null) PsiTreeUtil.getNextSiblingOfType(kElse, classOf[ScExpression]) else null
    if (e == null) None else Some(e)
  }

  def getLeftParenthesis = {
    val leftParenthesis = findChildByType(ScalaTokenTypes.tLPARENTHESIS)
    if (leftParenthesis == null) None else Some(leftParenthesis)
  }

  def getRightParenthesis = {
    val rightParenthesis = findChildByType(ScalaTokenTypes.tRPARENTHESIS)
    if (rightParenthesis == null) None else Some(rightParenthesis)
  }

  protected override def innerType(ctx: TypingContext) = {
    (thenBranch, elseBranch) match {
      case (Some(t), Some(e)) => for (tt <- t.getType(TypingContext.empty);
                                      et <- e.getType(TypingContext.empty)) yield {
        Bounds.weakLub(tt, et)
      }
      case (Some(t), None) => t.getType(TypingContext.empty).map(tt => Bounds.weakLub(tt, types.Unit))
      case _ => Failure(ScalaBundle.message("nothing.to.type"), Some(this))
    }
  }
}