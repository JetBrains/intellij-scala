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
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import base.ScTypeBoundsOwnerImpl
import psi.types.ScExistentialArgument

/** 
* @author Alexander Podkhalyuzin
* Date: 11.04.2008
*/

class ScWildcardTypeElementImpl(node: ASTNode) extends ScTypeBoundsOwnerImpl(node) with ScWildcardTypeElement {
  override def toString: String = "WildcardType"

  override def getType(implicit visited: Set[ScNamedElement]) = new ScExistentialArgument("_", Nil, lowerBound, upperBound)
}