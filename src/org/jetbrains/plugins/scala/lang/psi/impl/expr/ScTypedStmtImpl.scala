package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.expr._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.types.Any

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScTypedStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypedStmt {
  override def toString: String = "TypedStatement"

  protected override def innerType = typeElement match {
    case Some(te) => te.cachedType.unwrap(Any)
    case None => expr.getType
  }
}