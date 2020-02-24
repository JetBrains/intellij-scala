package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTagValue}
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createDocTagName
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocTagImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocTag{
  override def toString: String = "DocTag"

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTag(this)
  }

  override def getContainingComment: PsiDocComment =
    getParent match {
      case docComment: PsiDocComment => docComment
      case _ => null
    }

  override def getNameElement: PsiElement = findChildByType[PsiElement](ScalaDocTokenType.DOC_TAG_NAME)

  override def getDataElements: Array[PsiElement] = getChildren

  override def getValueElement: PsiDocTagValue = findChildByClass(classOf[PsiDocTagValue])
  
  override def getName: String =
    if (getNameElement != null) {
      getNameElement.getText
    } else {
      null
    }

  override def setName(name: String): PsiElement = {
    if (findChildByType[PsiElement](ScalaDocTokenType.DOC_TAG_NAME) != null) {
      findChildByType[PsiElement](ScalaDocTokenType.DOC_TAG_NAME).replace(createDocTagName(name))
    }

    this
  }

  override def getCommentDataText(): String =
    getNode.getChildren(TokenSet.create(ScalaDocTokenType.DOC_COMMENT_DATA)).map(_.getText).mkString("\n")

  override def getAllText(handler: PsiElement => String): String = 
    getNode
      .getChildren(TokenSet.orSet(TokenSet.create(ScalaDocTokenType.DOC_COMMENT_DATA), ScalaDocTokenType.ALL_SCALADOC_SYNTAX_ELEMENTS))
      .map(nd => handler(nd.getPsi))
      .mkString(" ")
}