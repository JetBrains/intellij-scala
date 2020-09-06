package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocInlinedTag, ScPsiDocToken}

class ScDocInlinedTagImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocInlinedTag {

  override def toString: String = "DocInlinedTag"

  override def name: String =
    nameElement.getText.stripPrefix("@")

  override def nameElement: ScPsiDocToken =
    findChildByType[ScPsiDocToken](ScalaDocTokenType.DOC_TAG_NAME)

  override def valueElement: Option[PsiElement] =
    nameElement
      .nextSiblings
      .filter { s =>
        s.elementType match {
          case ScalaDocTokenType.DOC_WHITESPACE | ScalaDocTokenType.DOC_INLINE_TAG_END =>
            false
          case _ =>
            s.getTextLength > 0
        }
      }
      .headOption

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitInlinedTag(this)
}