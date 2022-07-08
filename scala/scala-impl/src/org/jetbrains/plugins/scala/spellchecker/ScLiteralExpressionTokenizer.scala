package org.jetbrains.plugins.scala
package spellchecker

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.{EscapeSequenceTokenizer, TokenConsumer, Tokenizer}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

import java.util.Collections

class ScLiteralExpressionTokenizer extends Tokenizer[ScLiteral] {
  def processTextWithEscapeSequences(element: ScLiteral, text: String, consumer: TokenConsumer): Unit = {
    val unEscapedText = new java.lang.StringBuilder
    val offsets: Array[Int] = new Array[Int](text.length + 1)
    PsiLiteralExpressionImpl.parseStringCharacters(text, unEscapedText, offsets)
    EscapeSequenceTokenizer.processTextWithOffsets(element, consumer, unEscapedText, offsets, 1)
  }

  override def tokenize(element: ScLiteral, consumer: TokenConsumer): Unit = {
    val listOwner: PsiModifierListOwner = PsiTreeUtil.getParentOfType(element, classOf[PsiModifierListOwner])
    if (listOwner != null && AnnotationUtil.isAnnotated(listOwner, Collections.singleton(AnnotationUtil.NON_NLS), 0)) {
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

