package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScIfImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScIf {

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
      case _ => None
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

  protected override def innerType: TypeResult = {
    (thenBranch, elseBranch) match {
      case (Some(t), Some(e)) => for (tt <- t.`type`();
                                      et <- e.`type`()) yield {
        tt.lub(et)
      }
      case (Some(t), None) => t.`type`().map(_.lub(Unit))
      case _ => Failure(ScalaBundle.message("nothing.to.type"))
    }
  }

  override def toString: String = "IfStatement"
}