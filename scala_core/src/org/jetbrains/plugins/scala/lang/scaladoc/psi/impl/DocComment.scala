package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl


import com.intellij.lang.ASTNode
import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import api.ScDocComment
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import java.lang.String
import lexer.ScalaDocTokenType
import parser.ScalaDocElementTypes

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocCommentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocComment{
  def getTokenType: IElementType = ScalaDocElementTypes.SCALA_DOC_COMMENT
  override def toString: String = "DocComment"


  //todo: implement me
  def getTags: Array[PsiDocTag] = null

  def getDescriptionElements: Array[PsiElement] = null

  def findTagByName(name: String): PsiDocTag = null

  def findTagsByName(name: String): Array[PsiDocTag] = null
}