package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.psi.javadoc.PsiDocComment
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
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:55:13
*/

class ScTypeAliasDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeAliasDefinition {
  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)
  
  override def toString: String = "ScTypeAliasDefinition"

  override def getModifierList: ScModifierList = null

  def isDeprecated = false

  def getDocComment: PsiDocComment = null
}