package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.expr._
import psi.ScalaPsiElementImpl

import com.intellij.lang.ASTNode

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScTypedStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypedStmt {
  override def toString: String = "TypedStatement"

  override def getType = typeElement match {
    case Some(te) => te.getType
    case None => expr.getType
  }
}