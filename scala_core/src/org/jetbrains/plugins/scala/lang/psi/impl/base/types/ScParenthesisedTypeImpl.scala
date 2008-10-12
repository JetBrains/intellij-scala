package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import _root_.org.jetbrains.plugins.scala.lang.psi.types.Nothing
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

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScParenthesisedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParenthesisedTypeElement{
  override def toString: String = "TypeInParenthesis"

  override def getType() = typeElement match {
    case Some(te) => te.getType
    case None => Nothing
  }
}