package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

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

  def condition: Option[ScExpression] = {
    val rpar = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)
    val c = if (rpar != null) PsiTreeUtil.getPrevSiblingOfType(rpar, classOf[ScExpression]) else null
    Option(c)
  }

  def thenBranch: Option[ScExpression] = {
    val kElse = findChildByType[PsiElement](ScalaTokenTypes.kELSE)
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

  def elseBranch: Option[ScExpression] = {
    val kElse = findChildByType[PsiElement](ScalaTokenTypes.kELSE)
    val e = if (kElse != null) PsiTreeUtil.getNextSiblingOfType(kElse, classOf[ScExpression]) else null
    Option(e)
  }

  def getLeftParenthesis: Option[PsiElement] = {
    val leftParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    Option(leftParenthesis)
  }

  def getRightParenthesis: Option[PsiElement] = {
    val rightParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)
    Option(rightParenthesis)
  }

  protected override def innerType: TypeResult[ScType] = {
    (thenBranch, elseBranch) match {
      case (Some(t), Some(e)) => for (tt <- t.getType();
                                      et <- e.getType()) yield {
        tt.lub(et)
      }
      case (Some(t), None) => t.getType().map(_.lub(Unit))
      case _ => Failure(ScalaBundle.message("nothing.to.type"), Some(this))
    }
  }
}