package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.{Document, Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

private final class CompilerDiagnosticIntentionAction(
  override val getText: String,
  textEdits: Seq[(RangeMarker, String)]
) extends IntentionAction {
  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    !project.isDisposed && editor.getDocument.isWritable && file.isWritable && file.is[ScalaFile] &&
      textEdits.forall(_._1.isValid)
  }

  @RequiresWriteLock
  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val document = editor.getDocument
    applyDiagnostic(document, textEdits)
  }

  override def getFamilyName: String = getText

  override def startInWriteAction(): Boolean = true

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    val document = file.getFileDocument
    val textEditCopies = textEdits.map { case (marker, text) =>
      val markerCopy = document.createRangeMarker(marker.getStartOffset, marker.getEndOffset)
      (markerCopy, text)
    }
    applyDiagnostic(document, textEditCopies)
    IntentionPreviewInfo.DIFF
  }

  private def applyDiagnostic(document: Document, edits: Seq[(RangeMarker, String)]): Unit = {
    edits.foreach { case (marker, text) =>
      document.replaceString(marker.getStartOffset, marker.getEndOffset, text)
      marker.dispose()
    }
  }
}
