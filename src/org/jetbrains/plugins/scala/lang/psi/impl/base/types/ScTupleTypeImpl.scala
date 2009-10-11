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
/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTupleTypeElement{
  override def toString: String = "TupleType"

  override def getType(implicit visited: Set[ScNamedElement]) =
    new ScTupleType(collection.immutable.Seq(components.map[ScType, Seq[ScType]]({t: ScTypeElement => t.getType(visited)}).toSeq : _*))
}