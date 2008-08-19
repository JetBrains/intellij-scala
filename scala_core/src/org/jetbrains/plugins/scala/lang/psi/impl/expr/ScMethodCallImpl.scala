package org.jetbrains.plugins.scala.lang.psi.impl.expr

import types.ScFunctionType
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScMethodCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMethodCall {
  override def toString: String = "MethodCall"
  override def getType = getInvokedExpr.getType match {
    case ScFunctionType(r, _) => r
    case t => t
  }
}