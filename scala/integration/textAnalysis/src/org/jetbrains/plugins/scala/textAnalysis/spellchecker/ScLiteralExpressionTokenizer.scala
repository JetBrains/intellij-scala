package org.jetbrains.plugins.scala.textAnalysis.spellchecker

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiModifierListOwner}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.{EscapeSequenceTokenizer, TokenConsumer}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers.ScalaStringParser

import java.util.Collections

private final class ScLiteralExpressionTokenizer extends EscapeSequenceTokenizer[ScStringLiteral] {

  private def processTextWithEscapeSequences(
    element: ScStringLiteral,
    text: String,
    rangeInHost: TextRange,
    consumer: TokenConsumer
  ): Unit = {
    val unescapedText = new java.lang.StringBuilder
    val offsets: Array[Int] = new Array[Int](text.length + 1)

    // We gracefully handle incorrect escape sequences mostly for custom string interpolators (SCL-25082)
    val parser = ScalaStringParser.fromStringLiteral(element, offsets, exitOnEscapingWrongSymbol = false)
    val parseSuccessful = parser.parse(text, unescapedText)
    // expected to be true while `exitOnEscapingWrongSymbol` is false, but we still check just in case
    if (parseSuccessful) {
      val startOffset = rangeInHost.getStartOffset
      EscapeSequenceTokenizer.processTextWithOffsets(element, consumer, unescapedText, offsets, startOffset)
    }
  }

  override def tokenize(element: ScStringLiteral, consumer: TokenConsumer): Unit = {
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
      val rangeInHost = element.contentRangeInParent
      val contentTextOriginal = rangeInHost.substring(text)
      processTextWithEscapeSequences(element, contentTextOriginal, rangeInHost, consumer)
    }
  }

  override def getHighlightingRange(element: PsiElement, offset: Int, range: TextRange): TextRange = {
    super.getHighlightingRange(element, offset, range)
  }
}

