package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocElementType


/**
 * Represents a token inside a ScalaDoc comment.
 *
 * @see JavaDoc analogy [[com.intellij.psi.javadoc.PsiDocToken]]
 */
trait ScPsiDocToken extends PsiElement {
  /**
   * Returns the element type of this token.
   */
  def tokenType: ScalaDocElementType
}
