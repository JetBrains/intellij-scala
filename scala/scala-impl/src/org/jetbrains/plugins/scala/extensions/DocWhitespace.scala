package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType

object DocWhitespace {
  def unapply(e: PsiElement): Option[String] = e match {
    case leaf: LeafPsiElement if leaf.getElementType == ScalaDocTokenType.DOC_WHITESPACE => Some(leaf.getText)
    case _ => None
  }
}
