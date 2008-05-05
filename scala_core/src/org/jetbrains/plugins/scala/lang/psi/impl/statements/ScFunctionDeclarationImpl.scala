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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:08
*/

class ScFunctionDeclarationImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionDeclaration {
  def getNameNode: ASTNode = node.findChildByType(ScalaTokenTypes.tIDENTIFIER)
  override def toString: String = "ScFunctionDeclaration"

  override def getIcon(flags: Int) = Icons.FUNCTION

  def getParametersClauses: ScParamClauses = findChildByClass(classOf[ScParamClauses])

  def getReturnTypeNode: ScType = {
    findChildByClass(classOf[ScType]) 
  }
  def getTypeParam: ScTypeParamClause = {
    findChildByClass(classOf[ScTypeParamClause])
  }
}

