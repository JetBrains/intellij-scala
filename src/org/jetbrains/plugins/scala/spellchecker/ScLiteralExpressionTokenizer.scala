package org.jetbrains.plugins.scala
package spellchecker

import java.lang.{String, StringBuilder}
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.spellchecker.tokenizer.{TokenConsumer, Tokenizer, EscapeSequenceTokenizer}
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.codeInsight.AnnotationUtil
import java.util.Collections
import com.intellij.spellchecker.inspections.PlainTextSplitter
import lang.psi.api.base.ScLiteral

/**
 * @author Ksenia.Sautina
 * @since 2/3/13
 */

class ScLiteralExpressionTokenizer extends Tokenizer[ScLiteral] {
  def processTextWithEscapeSequences(element: ScLiteral, text: String, consumer: TokenConsumer) {
    val unEscapedText: StringBuilder = new StringBuilder
    val offsets: Array[Int] = new Array[Int](text.length + 1)
    PsiLiteralExpressionImpl.parseStringCharacters(text, unEscapedText, offsets)
    EscapeSequenceTokenizer.processTextWithOffsets(element, consumer, unEscapedText, offsets, 1)
  }

  def tokenize(element: ScLiteral, consumer: TokenConsumer) {
    val listOwner: PsiModifierListOwner = PsiTreeUtil.getParentOfType(element, classOf[PsiModifierListOwner])
    if (listOwner != null && AnnotationUtil.isAnnotated(listOwner, Collections.singleton(AnnotationUtil.NON_NLS), false, false)) {
      return
    }
    val text: String = element.getText
    if (text == null) {
      return
    }
    if (!text.contains("\\")) {
      consumer.consumeToken(element, PlainTextSplitter.getInstance)
    }
    else {
      processTextWithEscapeSequences(element, text, consumer)
    }
  }
}

