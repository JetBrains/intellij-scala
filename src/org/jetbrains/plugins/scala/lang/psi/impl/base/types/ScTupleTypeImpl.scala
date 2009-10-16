package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import collection.Set
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import psi.types.{ScType, ScTupleType}
import psi.types.result.TypingContext

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTupleTypeElement{
  override def toString: String = "TupleType"

  override def getType(ctx : TypingContext) =
    new ScTupleType(collection.immutable.Seq(components.map[ScType, Seq[ScType]]({t: ScTypeElement => t.getType(ctx)}).toSeq : _*))
}