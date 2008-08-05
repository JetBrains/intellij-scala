package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl






import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.util.PsiTreeUtil

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScIfStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScIfStmt {
  override def toString: String = "IfStatement"

  def condition = {
    val rpar = findChildByType(ScalaTokenTypes.tRPARENTHESIS)
    val c = if (rpar != null) PsiTreeUtil.getPrevSiblingOfType(rpar, classOf[ScExpression]) else null
    if (c == null) None else Some(c)
  }
}