package org.jetbrains.plugins.scala.lang.completion.filters

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

package object modifiers {
  def isAfterLeftParen(elem: PsiElement): Boolean = {
    val prev = elem.getPrevSibling

    prev != null && prev.getNode != null && prev.getNode.getElementType == ScalaTokenTypes.tLPARENTHESIS
  }
}
