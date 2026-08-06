package org.jetbrains.sbt.runner.utils

import com.intellij.execution.impl.{ConsoleViewImpl, EditorHyperlinkSupport}
import com.intellij.openapi.editor.Editor

import scala.jdk.CollectionConverters.CollectionHasAsScala

private object ConsoleInlayHintUtils {

  def inlayOffsetsAfterTextInConsole(consoleView: Option[ConsoleViewImpl], text: String): Seq[Int] = {
    val editor = consoleView.flatMap(editorFromFlushedConsole)
    val inlayOffsets = editor.map(inlayOffsetsAfterTextInEditor(_, text))
    inlayOffsets.getOrElse(Seq.empty)
  }

  private def editorFromFlushedConsole(console: ConsoleViewImpl): Option[Editor] = {
    console.flushDeferredText()
    Option(console.getEditor)
  }

  private def inlayOffsetsAfterTextInEditor(editor: Editor, text: String): Seq[Int] = {
    val documentText = editor.getDocument.getText
    val targetOffsets = occurrenceEndOffsets(documentText, text).toSet

    if (targetOffsets.isEmpty) Seq.empty
    else inlayOffsetsMatching(editor, targetOffsets)
  }

  //noinspection ApiStatus
  private def inlayOffsetsMatching(editor: Editor, targetOffsets: Set[Int]): Seq[Int] = {
    val editorHyperlinks = EditorHyperlinkSupport.get(editor)
    editorHyperlinks.waitForPendingFilters(30_000L)
    val inlays = editorHyperlinks.collectAllInlays().asScala
    val inlayOffsets = inlays.map(_.getOffset)
    inlayOffsets.filter(targetOffsets).toSeq
  }

  /**
   * Returns offsets immediately after every non-overlapping occurrence of `fragment` in `output`.
   */
  private def occurrenceEndOffsets(output: String, fragment: String): Seq[Int] = {
    val offsets = Seq.newBuilder[Int]
    var searchFrom = 0
    var index = output.indexOf(fragment, searchFrom)

    while (index >= 0) {
      offsets += index + fragment.length
      searchFrom = index + fragment.length
      index = output.indexOf(fragment, searchFrom)
    }

    offsets.result()
  }
}
