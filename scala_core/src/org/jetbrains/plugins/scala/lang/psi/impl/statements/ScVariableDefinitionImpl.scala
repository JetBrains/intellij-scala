package org.jetbrains.plugins.scala.lang.psi.impl.statements

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


import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:56:07
*/

class ScVariableDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScVariableDefinition{
  override def toString: String = "ScVariableDefinition"
  override def getIcon(flags: Int) = Icons.VAR
  def getIdentifierNodes: Array[ScalaPsiElement] = {
    if (getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER) != null) {
      val res = new Array[ScalaPsiElement](1);
      res(1) = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER).getPsi.asInstanceOf[ScalaPsiElement]
      return res
    }
    else if (findChildByClass(classOf[ScIdList]) != null){
      return findChildByClass(classOf[ScIdList]).getIdentifiers
    }
    else return new Array[ScalaPsiElement](0)
  }
}