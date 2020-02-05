package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.impl.{HighlightInfo, UpdateHighlightersUtil}
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.invokeLater

import scala.collection.JavaConverters._

object ExternalHighlighters {

  def updateOpenEditors[T: Highlightable](project: Project, infosByFile: VirtualFile => Seq[T]): Unit = {

    val editors = EditorFactory.getInstance().getAllEditors
      .filter(_.getProject == project)

    for (editor <- editors) {
      applyHighlighting(project, editor, infosByFile)
    }
  }

  def applyHighlighting[T: Highlightable](project: Project,
                                          editor: Editor,
                                          infosByFile: VirtualFile => Seq[T]): Unit = {

    for (vFile <- scalaFile(editor)) {

      invokeLater {
        val highlightInfos = infosByFile(vFile).map(toHighlightInfo(_, editor))

        val document = editor.getDocument
        UpdateHighlightersUtil.setHighlightersToEditor(
          project,
          document, 0, document.getTextLength,
          highlightInfos.asJava,
          editor.getColorsScheme,
          Pass.EXTERNAL_TOOLS
        )
      }
    }
  }

  private def scalaFileType = ScalaFileType.INSTANCE

  def scalaFile(editor: Editor): Option[VirtualFile] = {
    Option(FileDocumentManager.getInstance().getFile(editor.getDocument))
      .filter(vFile => vFile.getExtension == scalaFileType.getDefaultExtension)
  }

  def findRangeToHighlight(editor: Editor, offset: Int): Option[TextRange] = {
    ExternalHighlighters.scalaFile(editor)
      .flatMap { vFile =>
        val psiFile = Option(PsiManager.getInstance(editor.getProject).findFile(vFile))
        psiFile.map(_.findElementAt(offset).getTextRange)
      }
  }

  def toHighlightInfo[T: Highlightable](t: T, editor: Editor): HighlightInfo = {
    import Highlightable._

    val highlightRange =
      range(t, editor)
        .orElse(offset(t, editor).flatMap(findRangeToHighlight(editor, _)))
        .getOrElse(TextRange.EMPTY_RANGE)

    val highlightInfoType = HighlightInfo.convertSeverity(severity(t))

    HighlightInfo
      .newHighlightInfo(highlightInfoType)
      .range(highlightRange)
      .descriptionAndTooltip(message(t))
      .group(Pass.EXTERNAL_TOOLS)
      .create()
  }

}
