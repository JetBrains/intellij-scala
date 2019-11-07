package org.jetbrains.plugins.scala.codeInsight.hints.chain

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.{Document, Editor, Inlay, InlayModel}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

private class Group(hints: Seq[Hint],
                    minMargin: Int = 1,
                    maxMargin: Int = 6)
                   (inlayModel: InlayModel, document: Document, charWidthInPixel: Int) extends Disposable {

  private val minMarginInPixel = minMargin * charWidthInPixel
  private val maxMarginInPixel = maxMargin * charWidthInPixel

  private val alignmentLines: Seq[Line] = {
    def lineOf(expr: ScExpression): Int = document.getLineNumber(expr.getTextRange.getEndOffset)
    val lineToHintMapping = hints.groupBy(hint => lineOf(hint.expr)).mapValues(_.head)
    val lineHasHint = lineToHintMapping.contains _

    val firstLine = 0 max (lineOf(hints.head.expr) - 1)
    val lastLine = document.getLineCount min (lineOf(hints.last.expr) + 1)

    (firstLine to lastLine).flatMap { line =>
      val maybeHint = lineToHintMapping.get(line)
      val maybeOffset = maybeHint match {
        case Some(hint) => Some(hint.expr.getTextRange.getEndOffset)
        case _ if lineHasHint(line - 1) || lineHasHint(line + 1) => Some(document.getLineEndOffset(line))
        case _ => None
      }
      maybeOffset.map(new Line(_, maybeHint)(document))
    }
  }

  private val inlays: Seq[Inlay[Renderer]] = {
    val inlays = for (line <- alignmentLines; hint <- line.hint) yield {
      val inlay = inlayModel.addAfterLineEndElement(
        hint.expr.getTextRange.getEndOffset, false, new Renderer(line, hint.textParts, recalculateGroupsOffsetsIn))
      inlay.putUserData(ScalaExprChainKey, true)
      inlay
    }
    inlays.head.putUserData(ScalaExprChainDisposableKey, this)
    inlays
  }

  private def recalculateGroupsOffsetsIn(editor: Editor): Unit = {
    // unfortunately `AlignedHintsRenderer.getMargin -> recalculateGroupsOffsets`
    // is called by `inlayModel.addAfterLineEndElement` before inlays is actually set
    if (inlays == null)
      return

    val allEndXs = alignmentLines.map(_.lineEndXIn(editor))
    val actualEndXs = alignmentLines.withFilter(_.hint.isDefined).map(_.lineEndXIn(editor))
    val max = allEndXs.max
    val avg = actualEndXs.sum / actualEndXs.length
    var targetMaxX = max + math.max(minMarginInPixel, maxMarginInPixel - (max - avg) / 3)

    // this makes the group more stable and less flickery
    targetMaxX -= targetMaxX % charWidthInPixel

    for (inlay <- inlays) {
      val renderer = inlay.getRenderer
      val endX = renderer.line.lineEndXIn(editor)
      renderer.setMargin(endX, targetMaxX - endX, inlay)
    }
  }

  override def dispose(): Unit = alignmentLines.foreach(_.dispose())
}

