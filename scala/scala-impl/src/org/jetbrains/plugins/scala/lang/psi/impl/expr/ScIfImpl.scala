package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScIfImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScIf with ScBegin {

  override def condition: Option[ScExpression] = {
    def getPrecedingExpression(e: PsiElement): Option[ScExpression] =
      if (e != null) Option(PsiTreeUtil.getPrevSiblingOfType(e, classOf[ScExpression]))
      else           None

    val rpar = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)
    val cond = getPrecedingExpression(rpar)

    cond.orElse {
      if (this.isInScala3Module) {
        val thenKeyword = findChildByType[PsiElement](ScalaTokenType.ThenKeyword)
        getPrecedingExpression(thenKeyword)
      } else None
    }
  }

  override def thenExpression: Option[ScExpression] = {
    val kElse = findKElse
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

  override def elseExpression: Option[ScExpression] = {
    val kElse = findKElse
    val e = if (kElse != null) PsiTreeUtil.getNextSiblingOfType(kElse, classOf[ScExpression]) else null
    Option(e)
  }

  @inline
  override def elseKeyword: Option[PsiElement] = Option(findKElse)

  @inline
  private def findKElse: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.kELSE)

  override def leftParen: Option[PsiElement] = {
    val leftParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    Option(leftParenthesis)
  }

  override def rightParen: Option[PsiElement] = {
    val rightParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)
    Option(rightParenthesis)
  }

  override protected def innerType: TypeResult = {
    (thenExpression, elseExpression) match {
      case (Some(t), Some(e)) => for (tt <- t.`type`();
                                      et <- e.`type`()) yield {
        tt.lub(et)
      }
      case (Some(t), None) => t.`type`().map(_.lub(Unit))
      case _ => Failure(ScalaBundle.message("nothing.to.type"))
    }
  }

  override protected def keywordTokenType: IElementType = ScalaTokenTypes.kIF

  override def toString: String = "IfStatement"
}