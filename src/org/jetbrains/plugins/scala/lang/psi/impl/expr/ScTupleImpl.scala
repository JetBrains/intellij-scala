package org.jetbrains.plugins.scala.lang.psi.impl.expr

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

  override def getType = exprs match {
    case Array() => Unit
    case exprs => new ScTupleType(Seq(exprs map {_.cachedType} : _*))
  }
}