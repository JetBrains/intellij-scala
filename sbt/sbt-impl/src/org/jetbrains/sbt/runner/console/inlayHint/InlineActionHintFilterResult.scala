package org.jetbrains.sbt.runner.console.inlayHint

import com.intellij.codeInsight.hints.presentation.{PresentationFactory, PresentationRenderer}
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.InlayProvider
import com.intellij.openapi.editor.{Editor, EditorCustomElementRenderer}
import com.intellij.openapi.project.Project

/**
 * Filter the result item that renders the clickable action part as an editor inlay.
 *
 *
 * ### Implementation details
 * The presentation intentionally uses the same low-level inlay presentation API as several platform console action hints. \
 * For example [[com.intellij.debugger.impl.attach.JavaDebuggerConsoleFilterProvider]]
 * creates a rounded "Attach debugger" hint with `PresentationFactory.referenceOnHover`.
 *
 * For a button-style variant using `InlayButtonPresentationFactory`, see
 *  - [[com.intellij.debugger.impl.attach.JavaDebuggerAddExceptionBreakpointFilter]]
 *  - [[com.intellij.java.impl.nullaway.NullAwayInlayProvider]].
 */
//noinspection ApiStatus
private[console] final class InlineActionHintFilterResult(
  offset: Int,
  inlayHintActionText: String,
  inlayHintAction: Project => Unit,
) extends Filter.ResultItem(offset, offset, null)
  with InlayProvider {

  override def createInlayRenderer(editor: Editor): EditorCustomElementRenderer = {
    val factory = new PresentationFactory(editor)

    val actionTextRendered = factory.roundWithBackground(factory.smallText(inlayHintActionText))
    val actionHintPresentation = factory.referenceOnHover(actionTextRendered, (_, _) => {
      Option(editor.getProject).foreach(inlayHintAction)
    })

    val presentation = factory.seq(
      // The space is part of the inlay presentation, leaving the console text free of hidden markers.
      factory.smallText(" "),
      actionHintPresentation
    )
    new PresentationRenderer(presentation)
  }
}
