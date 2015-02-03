package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTagValue}
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocTagImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocTag{
  override def toString: String = "DocTag"

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => accept(s)
      case _ => super.accept(visitor)
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTag(this)
  }

  def getContainingComment: PsiDocComment =
    getParent match {
      case docComment: PsiDocComment => docComment
      case _ => null
    }

  def getNameElement: PsiElement = findChildByType[PsiElement](ScalaDocTokenType.DOC_TAG_NAME)

  def getDataElements: Array[PsiElement] = getChildren

  def getValueElement: PsiDocTagValue = findChildByClass(classOf[PsiDocTagValue])
  
  override def getName: String =
    if (getNameElement != null) {
      getNameElement.getText
    } else {
      null
    }

  def setName(name: String): PsiElement = {
    if (findChildByType[PsiElement](ScalaDocTokenType.DOC_TAG_NAME) != null) {
      findChildByType[PsiElement](ScalaDocTokenType.DOC_TAG_NAME).replace(ScalaPsiElementFactory.createDocTagName(name, getManager))
    }

    this
  }
}