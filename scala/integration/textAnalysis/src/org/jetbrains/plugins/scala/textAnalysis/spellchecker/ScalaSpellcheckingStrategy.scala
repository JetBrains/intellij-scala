package org.jetbrains.plugins.scala.textAnalysis.spellchecker

import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.{SpellcheckingStrategy, Tokenizer}
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

class ScalaSpellcheckingStrategy extends SpellcheckingStrategy {
  private final val myLiteralExpressionTokenizer: ScLiteralExpressionTokenizer = new ScLiteralExpressionTokenizer
  private final val myDocCommentTokenizer: ScalaDocCommentTokenizer = new ScalaDocCommentTokenizer

  override def getTokenizer(element: PsiElement): Tokenizer[? <: PsiElement] = element match {
    case _: ScLiteral => myLiteralExpressionTokenizer
    case _: ScDocComment => myDocCommentTokenizer
    case _ => super.getTokenizer(element)
  }

  override def isMyContext(element: PsiElement): Boolean = element.isVisible
}

