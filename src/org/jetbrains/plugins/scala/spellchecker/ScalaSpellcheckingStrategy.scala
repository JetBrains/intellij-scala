package org.jetbrains.plugins.scala
package spellchecker

import com.intellij.spellchecker.tokenizer.{Tokenizer, SpellcheckingStrategy}
import com.intellij.psi.PsiElement
import lang.psi.api.base.ScLiteral

/**
 * @author Ksenia.Sautina
 * @since 2/3/13
 */
class ScalaSpellcheckingStrategy extends SpellcheckingStrategy {
  override def getTokenizer(element: PsiElement): Tokenizer[_ <: PsiElement] = {
    if (element.isInstanceOf[ScLiteral]) {
      return myLiteralExpressionTokenizer
    }
    super.getTokenizer(element)
  }

  private final val myLiteralExpressionTokenizer: ScLiteralExpressionTokenizer = new ScLiteralExpressionTokenizer
}


