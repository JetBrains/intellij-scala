package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl


import com.intellij.lang.ASTNode
import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import api.ScDocComment
import com.intellij.psi.tree.IElementType
import lexer.ScalaDocTokenType
import parser.ScalaDocElementTypes

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocCommentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocComment{
  def getTokenType: IElementType = ScalaDocElementTypes.SCALA_DOC_COMMENT
  override def toString: String = "DocComment"
}