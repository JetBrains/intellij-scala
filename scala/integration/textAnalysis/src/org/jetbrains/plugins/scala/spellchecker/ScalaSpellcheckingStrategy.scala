package org.jetbrains.plugins.scala.spellchecker

import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.{SpellcheckingStrategy, Tokenizer}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

class ScalaSpellcheckingStrategy extends SpellcheckingStrategy {
  override def getTokenizer(element: PsiElement): Tokenizer[_ <: PsiElement] = {
    if (element.is[ScLiteral]) {
      return myLiteralExpressionTokenizer
    }
    super.getTokenizer(element)
  }

  private final val myLiteralExpressionTokenizer: ScLiteralExpressionTokenizer = new ScLiteralExpressionTokenizer
}


