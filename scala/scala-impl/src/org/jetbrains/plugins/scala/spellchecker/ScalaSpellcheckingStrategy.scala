package org.jetbrains.plugins.scala
package spellchecker

import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.{SpellcheckingStrategy, Tokenizer}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

class ScalaSpellcheckingStrategy extends SpellcheckingStrategy {
  override def getTokenizer(element: PsiElement): Tokenizer[_ <: PsiElement] = {
    if (element.isInstanceOf[ScLiteral]) {
      return myLiteralExpressionTokenizer
    }
    super.getTokenizer(element)
  }

  private final val myLiteralExpressionTokenizer: ScLiteralExpressionTokenizer = new ScLiteralExpressionTokenizer
}


