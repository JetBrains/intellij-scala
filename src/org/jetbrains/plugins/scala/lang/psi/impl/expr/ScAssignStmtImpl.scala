package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

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
  protected override def innerType = {
    getLExpression match {
      case call: ScMethodCall => call.getType
      case _ => Unit
    }
  }
}