package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.com.intellij.psi.util.PsiTreeUtil
import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

/**
 * @author Alexander Podkhalyuzin
 */

class ScReturnStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReturnStmt {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ReturnStatement"

  protected[expr] override def innerType(ctx: TypingContext) = Success(Nothing, Some(this))
    //Failure("Cannot infer type of `return' expression", Some(this))

  def returnKeyword: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.kRETURN)

  def returnFunction: Option[ScFunctionDefinition] = {
    val o = PsiTreeUtil.getParentOfType(this, classOf[ScFunctionDefinition])
    if (o == null) None else Some(o)
  }
}