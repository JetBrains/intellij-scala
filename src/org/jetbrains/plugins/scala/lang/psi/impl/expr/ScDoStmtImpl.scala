package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScDoStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDoStmt {
  override def toString: String = "DoStatement"

  def getExprBody: Option[ScExpression] = findChild(classOf[ScExpression])
  def hasExprBody: Boolean = {
    getExprBody match {
      case None => false
      case Some(_) => true
    }
  }

  def condition = {
    val rpar = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    val c = if (rpar != null) PsiTreeUtil.getNextSiblingOfType(rpar, classOf[ScExpression]) else null
    if (c == null) None else Some(c) 
  }

  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }
}