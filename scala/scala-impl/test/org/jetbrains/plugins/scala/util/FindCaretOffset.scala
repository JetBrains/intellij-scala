package org.jetbrains.plugins.scala.util

import com.intellij.testFramework.EditorTestUtil.CARET_TAG
import org.jetbrains.plugins.scala.extensions.StringExt
import org.junit.Assert.fail

object FindCaretOffset {
  def findCaretOffset(text: String, stripTrailingSpaces: Boolean): (String, Int) = {
    val (textActual, caretOffsets) = findCaretOffsets(text, stripTrailingSpaces)
    caretOffsets match {
      case Seq(caretIdx) => (textActual, caretIdx)
      case Seq() => (textActual, -1)
      case _ => fail(s"single caret expected but found: ${caretOffsets.size}").asInstanceOf[Nothing]
    }
  }

  def findCaretOffsets(text: String, trimText: Boolean): (String, Seq[Int]) = {

    val textNormalized = if (trimText) text.withNormalizedSeparator.trim else text.withNormalizedSeparator

    def caretIndex(offset: Int) = textNormalized.indexOf(CARET_TAG, offset)

    @scala.annotation.tailrec
    def collectCaretIndices(idx: Int)(indices: Seq[Int]): Seq[Int] =
      if (idx < 0) indices else {
        val nextIdx = caretIndex(idx + 1)
        collectCaretIndices(nextIdx)(indices :+ idx)
      }

    val caretIndices = collectCaretIndices(caretIndex(0))(Seq[Int]())
    val caretIndicesNormalized = caretIndices.zipWithIndex.map { case (caretIdx, idx) => caretIdx - idx * CARET_TAG.length }
    (
      textNormalized.replace(CARET_TAG, ""),
      caretIndicesNormalized
    )
  }

}
