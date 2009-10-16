package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import collection.Set
import com.intellij.lang.ASTNode
import api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types.ScCompoundType
import psi.types.result.TypingContext

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScCompoundTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCompoundTypeElement {
  override def toString: String = "CompoundType"

  override def getType(ctx: TypingContext) = {
    val comps = collection.immutable.Seq(components.map({_.getType(ctx).resType}).toSeq: _*)
    refinement match {
      case None => new ScCompoundType(comps, Seq.empty, Seq.empty)
      case Some(r) => new ScCompoundType(comps, r.holders.toList, r.types.toList)
    }
  }
}