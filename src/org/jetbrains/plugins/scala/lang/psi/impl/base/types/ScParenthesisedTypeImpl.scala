package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import _root_.org.jetbrains.plugins.scala.lang.psi.types.Unit
import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import collection.Set
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScParenthesisedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParenthesisedTypeElement{
  override def toString: String = "TypeInParenthesis"

  override def getType(implicit visited: Set[ScNamedElement]) = typeElement match {
    case Some(te) => te.getType(visited)
    case None => Unit
  }
}