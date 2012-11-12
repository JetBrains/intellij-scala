package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem._
import lang.psi.api.ScalaFile
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiComment, PsiManager, PsiFile}
import com.intellij.psi.impl.PsiManagerEx
import worksheet.WorksheetFoldingBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.icons.AllIcons
import com.intellij.lang.ASTNode
import extensions._
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author Ksenia.Sautina
 * @since 11/12/12
 */
class CleanWorksheetAction(file: VirtualFile) extends AnAction {
//  def CleanWorksheetAction() {}

  def actionPerformed(e: AnActionEvent) {
    val dataContext: DataContext = e.getDataContext
    val editor: Editor = PlatformDataKeys.EDITOR.getData(dataContext)
    val project: Project = PlatformDataKeys.PROJECT.getData(dataContext)
    assert(project != null)
    val psiFile: PsiFile = (PsiManager.getInstance(project).asInstanceOf[PsiManagerEx]).getFileManager.getCachedPsiFile(file)
    assert(psiFile != null)
    assert(editor != null)

    def cleanWorksheet(node: ASTNode, editor: Editor, project: Project) {
      val document = editor.getDocument
      invokeLater {
        inWriteAction {
          recStep(node, document)
        }
      }
    }

    def recStep(node: ASTNode, document: Document) {
      if (node.getPsi.isInstanceOf[PsiComment] &&
        (node.getText.startsWith(WorksheetFoldingBuilder.FIRST_LINE_PREFIX) || node.getText.startsWith(WorksheetFoldingBuilder.LINE_PREFIX))) {
        val line = document.getLineNumber(node.getPsi.getTextRange.getStartOffset)
        val startOffset = document.getLineStartOffset(line)
        val beginningOfTheLine = document.getText(new TextRange(startOffset, node.getPsi.getTextRange.getStartOffset))
        if (beginningOfTheLine.trim == "") document.deleteString(startOffset, node.getPsi.getTextRange.getEndOffset + 1)
        else document.deleteString(node.getPsi.getTextRange.getStartOffset, node.getPsi.getTextRange.getEndOffset)
        PsiDocumentManager.getInstance(project).commitDocument(document)
      }
      for (child <- node.getChildren(null)) {
        recStep(child, document)
      }
    }

    cleanWorksheet(psiFile.getNode, editor, project)
  }

  override def update(e: AnActionEvent) {
    val presentation = e.getPresentation
    presentation.setIcon(AllIcons.Actions.GC)

    def enable() {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable() {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }
    try {
      val file = LangDataKeys.PSI_FILE.getData(e.getDataContext)
      file match {
        case _: ScalaFile => enable()
        case _ => disable()
      }
    }
    catch {
      case e: Exception => disable()
    }
  }
}