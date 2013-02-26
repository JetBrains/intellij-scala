package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import api.ScalaElementVisitor
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import lexer.ScalaTokenTypes
import com.intellij.psi.util.PsiTreeUtil

/** 
 * Author: Alexander Podkhalyuzin
 * Date: 06.03.2008
 */
class ScCatchBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCatchBlock {
  override def toString: String = "CatchBlock"

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitCatchBlock(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitCatchBlock(this)
      case _ => super.accept(visitor)
    }
  }

  def getLeftParenthesis = {
    val leftParenthesis = findChildByType(ScalaTokenTypes.tLPARENTHESIS)
    if (leftParenthesis == null) None else Some(leftParenthesis)
  }

  def getRightParenthesis = {
    val rightParenthesis = findChildByType(ScalaTokenTypes.tRPARENTHESIS)
    if (rightParenthesis == null) None else Some(rightParenthesis)
  }

}