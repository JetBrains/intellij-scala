package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/
class ScTupleImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTuple {
  override def toString: String = "Tuple"

  override def getType = {
    if (exprs.length == 0) Unit
    else new ScTupleType(collection.immutable.Seq(exprs.map({p => p.cachedType}).toSeq : _*))
  }
}