package org.jetbrains.plugins.scala.codeInsight
package hints
package methodChains

import java.awt.{Graphics, Insets, Rectangle}

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.{Disposer, Key}
import org.jetbrains.plugins.scala.annotator.hints.Text
import org.jetbrains.plugins.scala.codeInsight.hints.methodChains.AlignedInlayGroup._
import org.jetbrains.plugins.scala.codeInsight.implicits.TextPartsHintRenderer
import org.jetbrains.plugins.scala.extensions._

private abstract class AlignedHintTemplate(val textParts: Seq[Text]) {
  def line(document: Document): Int = document.getLineNumber(endOffset)
  def endOffset: Int
}

private class AlignedInlayGroup(hints: Seq[AlignedHintTemplate],
                                minMargin: Int = 1,
                                maxMargin: Int = 6)
                               (inlayModel: InlayModel, document: Document, charWidthInPixel: Int) extends Disposable {
  private val minMarginInPixel = minMargin * charWidthInPixel
  private val maxMarginInPixel = maxMargin * charWidthInPixel

  private val alignmentLines: Seq[AlignmentLine] = {
    val lineToHintMapping = hints.groupBy(_.line(document)).mapValues(_.head)
    val lineHasHint = lineToHintMapping.contains _

    val firstLine = 0 max (hints.head.line(document) - 1)
    val lastLine = document.getLineCount min (hints.last.line(document) + 1)

    (firstLine to lastLine).flatMap { line =>
      val maybeHint = lineToHintMapping.get(line)
      val maybeOffset = maybeHint match {
        case Some(hint) => Some(hint.endOffset)
        case _ if lineHasHint(line - 1) || lineHasHint(line + 1) => Some(document.getLineEndOffset(line))
        case _ => None
      }
      maybeOffset.map(new AlignmentLine(_, maybeHint)(document))
    }
  }

  // unfortunately `AlignedHintsRenderer.getMargin -> recalculateGroupsOffsets`
  // is called by `inlayModel.addAfterLineEndElement`
  // so we set it to empty first, so it is not null while the inlays are being build
  private var inlays: Seq[Inlay[AlignedInlayRenderer]] = Seq.empty

  locally {
    inlays =
      for(line <- alignmentLines; hint <- line.maybeHint) yield {
        val inlay = inlayModel.addAfterLineEndElement(
          hint.endOffset,
          false,
          new AlignedInlayRenderer(line, hint.textParts, recalculateGroupsOffsets)
        )
        inlay.putUserData(ScalaMethodChainKey, true)
        inlay
      }
    inlays.head.putUserData(ScalaMethodChainDisposableKey, this)
  }

  private def recalculateGroupsOffsets(editor: Editor): Unit = {
    val allEndXs = alignmentLines.map(_.lineEndX(editor))
    val actualEndXs = alignmentLines.withFilter(_.hasHint).map(_.lineEndX(editor))
    val max = allEndXs.max
    val avg = actualEndXs.sum / actualEndXs.length
    var targetMaxX = max + math.max(minMarginInPixel, maxMarginInPixel - (max - avg) / 3)

    // this makes the group more stable and less flickery
    targetMaxX -= targetMaxX % charWidthInPixel

    for (inlay <- inlays) {
      val renderer = inlay.getRenderer
      val endX = renderer.line.lineEndX(editor)
      renderer.setMargin(endX, targetMaxX - endX, inlay, !editor.asOptionOf[EditorEx].exists(_.isPurePaintingMode))
    }
  }

  override def dispose(): Unit = alignmentLines.foreach(_.dispose())
}
private object AlignedInlayGroup {
  private val ScalaMethodChainDisposableKey: Key[Disposable] = Key.create[Disposable]("SCALA_METHOD_CHAIN_DISPOSABLE_KEY")

  def dispose(inlay: Inlay[_]): Unit = {
    inlay
      .getUserData(ScalaMethodChainDisposableKey)
      .nullSafe
      .foreach(Disposer.dispose)
  }

  private class AlignmentLine(offset: Int, val maybeHint: Option[AlignedHintTemplate])(document: Document) extends Disposable {
    private val marker: RangeMarker = document.createRangeMarker(offset, offset)

    def hasHint: Boolean = maybeHint.isDefined

    def lineNumber: Int = document.getLineNumber(marker.getEndOffset)

    def lineEndX(editor: Editor): Int = {
      val endOffset = marker.getEndOffset
      if (endOffset < 0 || endOffset >= document.getTextLength) 0
      else editor.offsetToXY(document.getLineEndOffset(lineNumber), true, false).x
    }

    override def dispose(): Unit = marker.dispose()
  }


  private case class Cached(lineEndX: Int, margin: Int)

  private class AlignedInlayRenderer(val line: AlignmentLine, textParts: Seq[Text], recalculateGroupsOffsets: Editor => Unit)
    extends TextPartsHintRenderer(textParts, typeHintsMenu) {

    private var cached: Cached = Cached(lineEndX = 0, margin = 0)

    def setMargin(lineEndX: Int, margin: Int, inlay: Inlay[_], repaint: Boolean): Unit = {
      if (cached.margin != margin) {
        cached = Cached(lineEndX, margin)

        if (repaint) {
          inlay.update()
        }
      }
    }

    override def paint_Adapted(editor: Editor, g: Graphics, r: Rectangle, textAttributes: TextAttributes): Unit = {
      if (cached.lineEndX != line.lineEndX(editor)) {
        val oldMargin = cached.margin
        recalculateGroupsOffsets(editor)
        // after recalculating the offset, r has the wrong width, so we fix that here
        r.width += cached.margin - oldMargin
      }

      var hasSomethingElseInLine = false
      editor.asOptionOf[EditorImpl].foreach(_.processLineExtensions(line.lineNumber, _ => { hasSomethingElseInLine = true; false} ))
      if (!hasSomethingElseInLine) {
        super.paint_Adapted(editor, g, r, textAttributes)
      }
    }

    override def getMargin(editor: Editor): Insets = new Insets(0, cached.margin, 0, 0)
  }
}