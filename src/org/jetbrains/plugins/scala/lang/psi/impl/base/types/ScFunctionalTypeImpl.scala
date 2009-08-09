package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.base.types._
import api.toplevel.ScNamedElement
import psi.ScalaPsiElementImpl
import lang.psi.types._
import com.intellij.lang.ASTNode
import collection.Set

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScFunctionalTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionalTypeElement {
  override def toString: String = "FunctionalType"

  override def getType(implicit visited: Set[ScNamedElement]) = {
    val ret = returnTypeElement match {
      case Some(r) => r.getType(visited).resType
      case None => Nothing
    }

    paramTypeElement match {
      case tup : ScTupleTypeElement => new ScFunctionType(ret, Seq(tup.components.map {_.getType(visited).resType}: _*))
      case other => new ScFunctionType(ret, Seq(other.getType(visited)))
    }
  }
}