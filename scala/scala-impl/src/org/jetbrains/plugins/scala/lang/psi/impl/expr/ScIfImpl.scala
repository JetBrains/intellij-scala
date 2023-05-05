package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.PsiElementExt
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

    val rpar = findRightParen
    val cond = getPrecedingExpression(rpar)

    cond.orElse {
      if (this.isInScala3File) {
        thenKeyword.flatMap(getPrecedingExpression)
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
  override def thenKeyword: Option[PsiElement] = Option(findChildByType[PsiElement](ScalaTokenType.ThenKeyword))

  @inline
  override def elseKeyword: Option[PsiElement] = Option(findKElse)

  @inline
  @Nullable
  private def findKElse: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.kELSE)

  @inline
  @Nullable
  private def findRightParen: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)

  override def leftParen: Option[PsiElement] = {
    val leftParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    Option(leftParenthesis)
  }

  override def rightParen: Option[PsiElement] = Option(findRightParen)

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
