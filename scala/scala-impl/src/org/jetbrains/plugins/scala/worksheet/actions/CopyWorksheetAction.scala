package org.jetbrains.plugins.scala
package worksheet.actions

import java.awt.datatransfer.StringSelection

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.editor._
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import javax.swing.Icon
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetFoldGroup

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
    if (psiFile == null) return

    var s = createMerged2(editor, viewer, psiFile)
    s = StringUtil.convertLineSeparators(s)
    val contents: StringSelection = new StringSelection(s)
    CopyPasteManager.getInstance.setContents(contents)
  }
  
  private def createMerged2(editor: Editor, viewer: Editor, psiFile: PsiFile): String = {
    val leftDocument = editor.getDocument
    if (viewer == null) return leftDocument.getText
    val rightDocument = viewer.getDocument
    
    val result = new StringBuilder
    val fullShift = StringUtil.repeat(" ", CopyWorksheetAction.COPY_BORDER)
    val lineSeparator = "\n"
    
    val mappings = WorksheetFoldGroup.computeMappings(viewer, editor, psiFile)

    def getFromDoc(lineNumber: Int, document: Document): CharSequence = {
      if (lineNumber >= document.getLineCount) "" else 
        document.getImmutableCharSequence.subSequence(
          document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber)
        )
    }

    def getLinesFrom(f: Int, t: Int, document: Document) = 
      for (i <- f until t) yield getFromDoc(i, document)
    
    def getLines(doc: Document) = getLinesFrom(0, doc.getLineCount, doc)
    
    def append2Result(leftLines: Seq[CharSequence], rightLines: Seq[CharSequence]) {
      leftLines.zipAll(rightLines, "", fullShift).foreach {
        case (textLeft, textRight) =>
          result.append(StringUtil.trimTrailing(textLeft))
          
          if (textRight.length() > 0) {
            for (_ <- 1 to (CopyWorksheetAction.COPY_BORDER - textLeft.length)) result append CopyWorksheetAction.FILL_SYMBOL
            result append " //"
            result append textRight
          }
          
          result append lineSeparator
      }
    }
    
    if (mappings.length < 2) append2Result(getLines(leftDocument), getLines(rightDocument)) else (mappings.head /: (mappings.tail :+ (leftDocument.getLineCount, rightDocument.getLineCount))) {
      case ((pl, pr), (l, r)) => 
        append2Result(getLinesFrom(pl, l, leftDocument), getLinesFrom(pr, r, rightDocument))
        (l, r)
    }
    
    result.toString()
  }

  override def actionIcon: Icon = AllIcons.Actions.Copy

  override def bundleKey = "worksheet.copy.button"
}

object CopyWorksheetAction {
  private val COPY_BORDER = 80
  private val FILL_SYMBOL = " "
}
