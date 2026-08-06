package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
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

  override def valueText: Option[String] = {
    val valueElements = nameElement
      .nextSiblings
      //inline tag starts with error element (for some reason) and whitespace
      .dropWhile(c => c.is[PsiErrorElement] || c.elementType == ScalaDocTokenType.DOC_WHITESPACE)
      .takeWhile(_.elementType != ScalaDocTokenType.DOC_INLINE_TAG_END)
    val result =
      Option.unless(valueElements.isEmpty)(valueElements.map(_.getText).mkString)
    result
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitInlinedTag(this)
}
