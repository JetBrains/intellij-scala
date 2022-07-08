package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api

class ScWhileImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScWhile with ScBegin {

  protected override def innerType = Right(api.Unit)

  override def condition: Option[ScExpression] = {
    // note: also remember Scala3 new syntax: `while condition do body`
    val whileKeyword = findChildByType[PsiElement](ScalaTokenTypes.kWHILE)
    val res = PsiTreeUtil.getNextSiblingOfType(whileKeyword, classOf[ScExpression])
    Option(res)
  }
  override def expression: Option[ScExpression] =
    condition.flatMap { c =>
      val res = PsiTreeUtil.getNextSiblingOfType(c, classOf[ScExpression])
      Option(res)
    }

  override def leftParen: Option[PsiElement] = {
    val leftParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    Option(leftParenthesis)
  }

  override def rightParen: Option[PsiElement] = {
    val rightParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)
    Option(rightParenthesis)
  }

  override protected def keywordTokenType: IElementType = ScalaTokenTypes.kWHILE

  override def toString: String = "WhileStatement"
}