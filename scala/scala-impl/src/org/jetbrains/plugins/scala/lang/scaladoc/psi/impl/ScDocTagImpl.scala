package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTagValue}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createDocTagName
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

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
    if (getNameElement != null)
      getNameElement.getText
    else
      null

  override def setName(name: String): PsiElement = {
    val tagNameNode = findChildByType[PsiElement](ScalaDocTokenType.DOC_TAG_NAME)
    if (tagNameNode != null)
      tagNameNode.replace(createDocTagName(name))

    this
  }
}