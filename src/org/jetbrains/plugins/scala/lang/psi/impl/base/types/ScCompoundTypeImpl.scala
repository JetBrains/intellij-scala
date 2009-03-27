package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl




import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._

import icons.Icons

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