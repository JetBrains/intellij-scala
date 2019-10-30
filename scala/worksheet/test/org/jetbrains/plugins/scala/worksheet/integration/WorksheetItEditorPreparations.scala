package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditor, FileEditorManager, TextEditor}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings
import org.junit.Assert.{assertNotNull, fail}

trait WorksheetItEditorPreparations {
  self: WorksheetIntegrationBaseTest =>

  protected def prepareWorksheetEditor(before: String): Editor = {
    val (vFile, psiFile) = createWorksheetFile(before)

    val settings = WorksheetCommonSettings(psiFile)
    setupWorksheetSettings(settings)

    val worksheetEditor = openEditor(vFile)
    worksheetEditor
  }

  private def createWorksheetFile(before: String): (VirtualFile, PsiFile) = {
    val fileName = worksheetFileName

    val vFile = addFileToProjectSources(fileName, before)
    assertNotNull(vFile)

    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    assertNotNull(psiFile)
    (vFile, psiFile)
  }

  private def openEditor(vFile: VirtualFile): Editor = {
    val editors: Array[FileEditor] = EdtTestUtil.runInEdtAndGet { () =>
      FileEditorManager.getInstance(project).openFile(vFile, true)
    }

    editors match {
      case Array(e: Editor)     => e
      case Array(e: TextEditor) => e.getEditor
      case _                    => fail(s"couldn't fond any opened editor for file $vFile").asInstanceOf[Nothing]
    }
  }
}
