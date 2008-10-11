package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import api.base.types._
import psi.ScalaPsiElementImpl
import lang.psi.types._
import com.intellij.lang.ASTNode

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScFunctionalTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionalTypeElement {
  override def toString: String = "FunctionalType"

  override def getType = {
    val ret = returnTypeElement match {
      case Some(r) => r.getType
      case None => Nothing
    }

    paramTypeElement match {
      case tup : ScTupleTypeElement => new ScFunctionType(ret, tup.components.map {_.getType})
      case other => new ScFunctionType(ret, Seq.singleton(other.getType))
    }
  }
}