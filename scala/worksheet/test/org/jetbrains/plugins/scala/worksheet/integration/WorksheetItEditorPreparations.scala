package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.{FileEditor, FileEditorManager, TextEditor}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.WorksheetEditorAndFile
import org.jetbrains.plugins.scala.worksheet.settings.persistent.WorksheetFilePersistentSettings
import org.jetbrains.plugins.scala.worksheet.{WorksheetFile, WorksheetLanguage}
import org.junit.Assert.{assertNotNull, fail}

trait WorksheetItEditorPreparations {
  self: WorksheetIntegrationBaseTest =>

  protected def prepareWorksheetEditor(before: String, scratchFile: Boolean = false): WorksheetEditorAndFile = {
    val (vFile, psiFile) = createWorksheetFile(before.withNormalizedSeparator, scratchFile)

    val settings = WorksheetFilePersistentSettings(psiFile.getVirtualFile)
    setupWorksheetSettings(settings)

    val worksheetEditor = openEditor(vFile)
    WorksheetEditorAndFile(worksheetEditor, psiFile)
  }

  private def createWorksheetFile(before: String, scratchFile: Boolean): (VirtualFile, WorksheetFile) = {
    val fileName = worksheetFileName

    val vFile = if (scratchFile) {
      val file = ScratchRootType.getInstance.createScratchFile(project, fileName, ScalaLanguage.INSTANCE, before)
      /**
       * Hack to inject a proper language into a newly-created scratch file.
       * Scratch file creation is hacky, couldn't find any nicer solution.
       *
       * @see [[org.jetbrains.plugins.scala.worksheet.ScalaScratchFileCreationHelper]])
       * @see [[com.intellij.ide.scratch.ScratchFileActions.doCreateNewScratch]]
       */
      ScratchFileService.getInstance.getScratchesMapping.setMapping(file, WorksheetLanguage.INSTANCE)
      file
    } else {
      addFileToProjectSources(fileName, before)
    }
    assertNotNull(vFile)

    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    assertNotNull(psiFile)
    (vFile, psiFile.asInstanceOf[WorksheetFile])
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
