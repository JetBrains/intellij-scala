package org.jetbrains.plugins.scala
package incremental

import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoPostFilter}
import com.intellij.openapi.util.TextRange

class Filter extends HighlightInfoPostFilter {
  override def accept(highlightInfo: HighlightInfo): Boolean = {
    val editor = Highlighting.editor

    if (editor == null) return true

    if (!incremental.Highlighting.enabledIn(editor.getProject)) return true

    val visibleRange = VisibleRange.in(editor)
    if (visibleRange == null) return true

    val highlightRange = TextRange.create(highlightInfo.startOffset, highlightInfo.endOffset)

    highlightRange.intersects(visibleRange)
  }
}
