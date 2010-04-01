package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.com.intellij.psi.util.PsiTreeUtil
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types.Nothing
import com.intellij.psi.PsiElement
import lexer.ScalaTokenTypes
import types.result.{TypingContext, Failure, Success}
import api.statements.ScFunctionDefinition

/**
 * @author Alexander Podkhalyuzin
 */

class ScReturnStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReturnStmt {
  override def toString: String = "ReturnStatement"

  protected[expr] override def innerType(ctx: TypingContext) = Success(Nothing, Some(this))
    //Failure("Cannot infer type of `return' expression", Some(this))

  def returnKeyword: PsiElement = findChildByType(ScalaTokenTypes.kRETURN)

  def returnFunction: Option[ScFunctionDefinition] = {
    val o = PsiTreeUtil.getParentOfType(this, classOf[ScFunctionDefinition])
    if (o == null) None else Some(o)
  }
}