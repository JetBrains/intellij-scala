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
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:55:53
*/

class ScVariableDeclarationImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScVariableDeclaration {

  override def toString: String = "ScVariableDeclaration"

  override def getIcon(flags: Int) = Icons.VAR

  def getIdentifierNodes: Array[PsiElement] = {
    if (getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER) != null) {
      val res = new Array[PsiElement](1);
      val temp = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER).getPsi
      res(0) = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER).getPsi
      return res
    }
    else if (findChildByClass(classOf[ScIdList]) != null){
      return findChildByClass(classOf[ScIdList]).getIdentifiers
    }
    else return new Array[PsiElement](0)
  }
}