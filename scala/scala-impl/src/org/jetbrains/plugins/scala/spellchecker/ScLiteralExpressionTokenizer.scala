package org.jetbrains.plugins.scala.spellchecker

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.{EscapeSequenceTokenizer, TokenConsumer}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

import java.util.Collections

final class ScLiteralExpressionTokenizer extends EscapeSequenceTokenizer[ScLiteral] {

  private def processTextWithEscapeSequences(
    element: ScLiteral,
    text: String,
    rangeInHost: TextRange,
    consumer: TokenConsumer
  ): Unit = {
    val unescapedText = new java.lang.StringBuilder
    val offsets: Array[Int] = new Array[Int](text.length + 1)
    PsiLiteralExpressionImpl.parseStringCharacters(text, unescapedText, offsets)
    val startOffset = rangeInHost.getStartOffset
    EscapeSequenceTokenizer.processTextWithOffsets(element, consumer, unescapedText, offsets, startOffset)
  }

  override def tokenize(element: ScLiteral, consumer: TokenConsumer): Unit = {
    val listOwner: PsiModifierListOwner = PsiTreeUtil.getParentOfType(element, classOf[PsiModifierListOwner])
    if (listOwner != null && AnnotationUtil.isAnnotated(listOwner, Collections.singleton(AnnotationUtil.NON_NLS), 0)) {
      return
    }
    val text: String = element.getText
    if (text == null)
      return

    if (!text.contains("\\")) {
      consumer.consumeToken(element, PlainTextSplitter.getInstance)
    }
    else {
      val rangeInHost = element.contentRange.shiftLeft(element.getNode.getStartOffset)
      val contentTextOriginal = rangeInHost.substring(text)
      processTextWithEscapeSequences(element, contentTextOriginal, rangeInHost, consumer)
    }
  }
}

