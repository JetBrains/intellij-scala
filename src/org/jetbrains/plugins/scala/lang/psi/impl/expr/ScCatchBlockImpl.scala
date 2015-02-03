package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._

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
    val leftParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    if (leftParenthesis == null) None else Some(leftParenthesis)
  }

  def getRightParenthesis = {
    val rightParenthesis = findChildByType[PsiElement](ScalaTokenTypes.tRPARENTHESIS)
    if (rightParenthesis == null) None else Some(rightParenthesis)
  }

}