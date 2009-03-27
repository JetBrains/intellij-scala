package org.jetbrains.plugins.scala.lang.psi.impl.expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types.Nothing

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScReturnStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReturnStmt {
  override def toString: String = "ReturnStatement"

  override def getType = Nothing
}