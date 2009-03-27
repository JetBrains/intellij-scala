package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl






import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
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
    val rpar = findChildByType(ScalaTokenTypes.tLPARENTHESIS)
    val c = if (rpar != null) PsiTreeUtil.getNextSiblingOfType(rpar, classOf[ScExpression]) else null
    if (c == null) None else Some(c) 
  }
}