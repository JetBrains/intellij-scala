package org.jetbrains.plugins.scala
package worksheet.actions

import java.awt.datatransfer.StringSelection
import javax.swing.Icon

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.editor._
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import worksheet.runconfiguration.WorksheetCache
import worksheet.ui.WorksheetFoldRegionDelegate

/**
 * @author Dmitry.Naydanov
 * @author Ksenia.Sautina
 * @since 12/6/12
 */
class CopyWorksheetAction extends AnAction with TopComponentAction {
  def actionPerformed(e: AnActionEvent) {
    val project = e.getProject
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    if (editor == null) return

    val psiFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    val viewer = WorksheetCache.getInstance(project).getViewer(editor)
    if (psiFile == null || viewer == null) return

    var s = createMerged(editor, viewer)
    s = StringUtil.convertLineSeparators(s)
    val contents: StringSelection = new StringSelection(s)
    CopyPasteManager.getInstance.setContents(contents)
  }

  private def createMerged(editor: Editor, viewer: Editor): String = {
    val result = new StringBuilder
    val fullShift = StringUtil.repeat(" ", CopyWorksheetAction.COPY_BORDER)
    val lineSeparator = "\n"

    val leftDocument = editor.getDocument
    val rightDocument = viewer.getDocument

    def append2Result(textLeft: String, textRight: String, sym: String) {
      result append ( if (textLeft.length < CopyWorksheetAction.COPY_BORDER) textLeft else textLeft.substring(0, CopyWorksheetAction.COPY_BORDER))
      for (_ <- 1 to (CopyWorksheetAction.COPY_BORDER - textLeft.length)) result append sym
      result append "//"
      result append textRight
      result append lineSeparator
    }

    def getFromDoc(lineNumber: Int, document: Document) = if (document.getLineCount > lineNumber) document getText {
      new TextRange(document getLineStartOffset lineNumber, document getLineEndOffset lineNumber)
    } else ""

    def getFromLeft(lineNumber: Int) = getFromDoc(lineNumber, leftDocument)

    def getFromRight(lineNumber: Int) = getFromDoc(lineNumber, rightDocument)

    val marker = viewer.getFoldingModel.asInstanceOf[FoldingModelImpl].getAllFoldRegions find {
      case _: WorksheetFoldRegionDelegate => true
      case _ => false
    }

    var lastLeftEnd  = 0
    var lastRightEnd = 0

    marker map {
      case m: WorksheetFoldRegionDelegate => (0 /: m.getWorksheetGroup.getCorrespondInfo) {
        case (lastEnd, (rightStartOffset, rightEndOffset, leftOffset, spaces, leftLength)) =>
          val leftStart = {
            var j = lastEnd

            while (getFromLeft(j).trim.length == 0 && j < leftDocument.getLineCount) j += 1
            if (j == leftDocument.getLineCount) return result.toString() else j
          }
          val currentLeftStart = leftDocument getLineNumber leftOffset
          val leftEnd = leftDocument getLineNumber leftOffset // + spaces

          val rightStart = rightDocument getLineNumber rightStartOffset
          val rightEnd = rightDocument getLineNumber rightEndOffset

          for (_ <- lastEnd until leftStart) {
            append2Result(" ", " ", " ")
          }

          for (i <- leftStart to leftEnd) {
            val txt = getFromLeft(i)

            append2Result(txt, getFromRight(rightStart + i - currentLeftStart), " ")
          }

          if (spaces > 0) for (j <- (spaces - 1).to(0, -1)) {
            result append fullShift
            result append "//"
            result append {
              rightDocument getText {
                new TextRange(rightDocument getLineStartOffset (rightEnd - j), rightDocument getLineEndOffset (rightEnd - j))
              }
            }
            result append lineSeparator
          }

          lastLeftEnd = leftEnd + 1
          lastRightEnd = rightEnd + 1

          (leftDocument getLineNumber leftOffset) + leftLength
      }
    }

    for (i <- 0 until (leftDocument.getLineCount - lastLeftEnd))
      append2Result(getFromLeft(lastLeftEnd + i), getFromRight(lastRightEnd + i), " ")

    result.toString()
  }

  override def actionIcon: Icon = AllIcons.Actions.Copy

  override def bundleKey = "worksheet.copy.button"
}

object CopyWorksheetAction {
  private val COPY_BORDER = 80
}
