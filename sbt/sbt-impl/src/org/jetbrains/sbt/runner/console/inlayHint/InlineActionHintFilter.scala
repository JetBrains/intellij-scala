package org.jetbrains.sbt.runner.console.inlayHint

import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * Console filter that recognizes a marker line and contributes an inline action hint at the end of it.
 *
 *
 * ### Implementation details
 * This follows the same console-filter contract as platform implementations that return result items with `InlayProvider`s.
 * The execution console later consumes those items in [[com.intellij.execution.impl.EditorHyperlinkSupport]],
 * while [[com.intellij.execution.filters.CompositeFilter]] explains why a single-item result needs special care.
 *
 * Similar platform examples:
 *  - [[com.intellij.debugger.impl.attach.JavaDebuggerConsoleFilterProvider]] adds an "Attach debugger" inlay for debugger listening lines
 *  - [[com.intellij.java.impl.nullaway.NullAwayFilter]] contributes console inlay results for NullAway diagnostics
 */
private[console] final class InlineActionHintFilter(
  markerLineText: String,
  inlayHintActionText: String,
  inlayHintAction: Project => Unit
) extends Filter {

  override def applyFilter(line: String, entireLength: Int): Filter.Result = {
    if (line.stripLineEnd != markerLineText)
      return null

    val resultItems = Seq(
      createInlineActionHint(line, entireLength),
      // Keep a second item so CompositeFilter does not unwrap the result and drop the InlayProvider.
      new Filter.ResultItem(0, 0, null),
    )

    new Filter.Result(resultItems.asJava)
  }

  private def createInlineActionHint(line: String, entireLength: Int): InlineActionHintFilterResult = {
    val startOffset = entireLength - line.length
    // Console inlays are placed at the result item's end offset; use the end of the status text.
    val inlayOffset = startOffset + markerLineText.length

    new InlineActionHintFilterResult(
      inlayOffset,
      inlayHintActionText,
      inlayHintAction
    )
  }
}
