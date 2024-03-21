package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.{Document, Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.compiler.diagnostics.Action
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.jdk.CollectionConverters._

private final class CompilerDiagnosticIntentionAction private (
  override val getText: String,
  textEdits: Seq[(RangeMarker, String)]
) extends IntentionAction {
  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    !project.isDisposed && editor.getDocument.isWritable && file.isWritable && file.is[ScalaFile] &&
      textEdits.forall(_._1.isValid)
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val document = editor.getDocument
    applyDiagnostic(project, file, document, textEdits)
  }

  override def getFamilyName: String = getText

  override def startInWriteAction(): Boolean = true

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    val document = file.getFileDocument
    val textEditCopies = textEdits.map { case (marker, text) =>
      val markerCopy = document.createRangeMarker(marker.getStartOffset, marker.getEndOffset)
      markerCopy.setGreedyToRight(true)
      (markerCopy, text)
    }
    applyDiagnostic(project, file, document, textEditCopies)
    IntentionPreviewInfo.DIFF
  }

  private def applyDiagnostic(
    project: Project,
    file: PsiFile,
    document: Document,
    edits: Seq[(RangeMarker, String)]
  ): Unit = {
    if (edits.isEmpty) return

    edits.foreach { case (marker, text) =>
      val range = marker.getTextRange
      document.replaceString(range.getStartOffset, range.getEndOffset, text)
    }

    document.commit(project)
    val ranges = edits.map { case (marker, _) => growLeftAndRight(marker.getTextRange, document) }.asJava
    CodeStyleManager.getInstance(project).reformatText(file, ranges, false)

    edits.foreach { case (marker, _) => marker.dispose() }
  }

  private def growLeftAndRight(range: TextRange, document: Document): TextRange = {
    val text = document.getCharsSequence

    var startOffset = range.getStartOffset
    while (!text.charAt(startOffset).isWhitespace) {
      startOffset -= 1
    }

    var endOffset = range.getEndOffset
    while (!text.charAt(endOffset).isWhitespace) {
      endOffset += 1
    }

    TextRange.create(startOffset, endOffset)
  }
}

private object CompilerDiagnosticIntentionAction {
  def create(document: Document, action: Action): CompilerDiagnosticIntentionAction = {
    val text = action.title.capitalize
    val markers = action.edit.changes.map { te =>
      val start = document.getLineStartOffset(te.start.line - 1) + te.start.column - 1
      val end = document.getLineStartOffset(te.end.line - 1) + te.end.column - 1
      val marker = document.createRangeMarker(start, end)
      marker.setGreedyToRight(true)
      (marker, te.newText)
    }
    new CompilerDiagnosticIntentionAction(text, markers)
  }
}
