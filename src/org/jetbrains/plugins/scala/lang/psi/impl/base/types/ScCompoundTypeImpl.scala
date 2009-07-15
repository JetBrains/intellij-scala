package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import collection.Set
import com.intellij.lang.ASTNode
import api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types.ScCompoundType

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScCompoundTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCompoundTypeElement {
  override def toString: String = "CompoundType"

  override def getType(implicit visited: Set[ScNamedElement]) = {
    val comps = components.map {_.getType(visited).resType}
    refinement match {
      case None => new ScCompoundType(comps, Seq.empty, Seq.empty)
      case Some(r) => new ScCompoundType(comps, r.holders, r.types)
    }
  }
}