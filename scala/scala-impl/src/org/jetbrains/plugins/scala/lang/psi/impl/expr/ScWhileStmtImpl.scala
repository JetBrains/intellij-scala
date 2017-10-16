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
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.lang.psi.types.result.Success

/**
* @author Alexander.Podkhalyuzin
*/

class ScWhileStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScWhileStmt {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "WhileStatement"

  def condition: Option[ScExpression] = {
    val rpar = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)
    val c = if (rpar != null) PsiTreeUtil.getPrevSiblingOfType(rpar, classOf[ScExpression]) else null
    Option(c)
  }

  def body: Option[ScExpression] = {
    val rpar = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)
    val c = if (rpar != null) PsiTreeUtil.getNextSiblingOfType(rpar, classOf[ScExpression]) else null
    Option(c)
  }

  def getLeftParenthesis: Option[PsiElement] = {
    val leftParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    Option(leftParenthesis)
  }

  def getRightParenthesis: Option[PsiElement] = {
    val rightParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)
    Option(rightParenthesis)
  }


  protected override def innerType = Success(api.Unit, Some(this))
}