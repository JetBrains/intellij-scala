package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import psi.ScalaPsiElementImpl
import api.base.types._
import lang.psi.types.ScExistentialType

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._



/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScExistentialTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScExistentialTypeElement{
  override def toString: String = "ExistentialType"

  override def getType() = new ScExistentialType(quantified.getType, clause.declarations)
}