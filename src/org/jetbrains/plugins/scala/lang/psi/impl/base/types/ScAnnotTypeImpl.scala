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
import collection.Set

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScAnnotTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScAnnotTypeElement{
  override def toString: String = "TypeWithAnnotation"

  override def getType(implicit visited: Set[ScNamedElement]) = typeElement.getType(visited)
}