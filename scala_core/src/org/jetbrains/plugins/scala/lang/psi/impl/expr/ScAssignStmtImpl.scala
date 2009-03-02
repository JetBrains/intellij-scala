package org.jetbrains.plugins.scala.lang.psi.impl.expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types.Unit

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScAssignStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScAssignStmt {
  override def toString: String = "AssignStatement"
  override def getType = Unit //todo:
}