package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl




import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types.ScTupleType

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScTupleTypeElement{
  override def toString: String = "TupleType"

  override def getType() = new ScTupleType(components.map {te => te.getType})
}