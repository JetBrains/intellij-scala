package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import base.ScTypeBoundsOwnerImpl
import psi.types.ScExistentialArgument
import collection.Set

/** 
* @author Alexander Podkhalyuzin
* Date: 11.04.2008
*/

class ScWildcardTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeBoundsOwnerImpl with ScWildcardTypeElement {
  override def toString: String = "WildcardType"

  override def getType(implicit visited: Set[ScNamedElement]) = new ScExistentialArgument("_", Nil, lowerBound, upperBound)
}