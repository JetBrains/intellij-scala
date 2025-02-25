package org.jetbrains.plugins.scala
package incremental

import incremental.Updater._

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.{EditorEx, MarkupModelEx}
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.PsiManager

import java.awt.Color
import javax.swing.Timer

private class Updater(editor: Editor) extends Disposable {
  private val updateTimer = {
    val timer = new Timer(UPDATE_DELAY, _ => update())
    timer.setRepeats(false)
    timer
  }

  private var previousVisibleRange: TextRange = _

  def scheduleUpdate(delta: Boolean): Unit = {
    if (!delta) {
      previousVisibleRange = null
    }

    updateTimer.restart()
  }

  private def update(): Unit = {
    VisibleRange.saveIn(editor)

    val visibleRange = VisibleRange.in(editor)

    val exactVisibleRange = VisibleRange.exactIn(editor)
    val editorEx = editor.asInstanceOf[EditorEx]
    Seq(editorEx.getMarkupModel, editorEx.getFilteredDocumentMarkupModel).foreach { model =>
      concealErrorStripeMarksOutside(exactVisibleRange, model, editor.getColorsScheme)
      revealErrorStripeMarksInside(exactVisibleRange, model)
    }

    val newlyVisibleRange = if (previousVisibleRange == null) visibleRange else visibleRange.diff(previousVisibleRange)

    val document = PsiManager.getInstance(editor.getProject).findFile(editor.getVirtualFile).getViewProvider.getDocument
    val daemon = DaemonCodeAnalyzer.getInstance(editor.getProject)
    daemon.combineDirtyScopes(document, newlyVisibleRange)
    daemon.stopProcess(/* restart = */ true)

    previousVisibleRange = visibleRange
  }

  override def dispose(): Unit = {
    updateTimer.stop()
  }
}

private object Updater {
  private val UPDATE_DELAY = 200 // ms

  private val ERROR_STRIPE_MARK_COLOR_KEY = Key.create[Color]("error_stripe_mark_color")

  private def concealErrorStripeMarksOutside(visibleRange: TextRange, markupModel: MarkupModelEx, colorScheme: EditorColorsScheme): Unit = {
    val backgroundColor = colorScheme.getDefaultBackground
    markupModel.processRangeHighlightersOutside(visibleRange.getStartOffset, visibleRange.getEndOffset, highlighter => {
      val actualColor = highlighter.getErrorStripeMarkColor(colorScheme)
      if (!highlighter.isThinErrorStripeMark && actualColor != null && actualColor != backgroundColor) {
        highlighter.putUserData(ERROR_STRIPE_MARK_COLOR_KEY, actualColor)
        highlighter.setErrorStripeMarkColor(backgroundColor)
      }
      true
    })
  }

  private def revealErrorStripeMarksInside(visibleRange: TextRange, markupModel: MarkupModelEx): Unit = {
    markupModel.processRangeHighlightersOverlappingWith(visibleRange.getStartOffset, visibleRange.getEndOffset, highlighter => {
      val savedColor = highlighter.getUserData(ERROR_STRIPE_MARK_COLOR_KEY)
      if (savedColor != null) {
        highlighter.setErrorStripeMarkColor(savedColor)
        highlighter.putUserData(ERROR_STRIPE_MARK_COLOR_KEY, null)
      }
      true
    })
  }
}
