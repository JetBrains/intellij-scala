package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{DataContext, LangDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.{PsiDirectory, PsiManager}
import com.intellij.testFramework.EditorTestUtil
import com.intellij.util.ui.TextTransferable
import org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProviderInSbtProjectTestLike.ExpectedPasteTestOutcome
import org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProviderInSbtProjectTestLike.ExpectedPasteTestOutcome.AddToExistingFile
import org.jetbrains.plugins.scala.extensions.{StringExt, inWriteAction}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.{assertCollectionEquals, assertCollectionIsEmpty}
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}

trait ScalaFilePasteProviderInSbtProjectTestLike {

  import ScalaFilePasteProviderInSbtProjectTestLike.VfsUtils._

  protected final val Caret = EditorTestUtil.CARET_TAG

  protected def getProject: Project

  protected def doPasteToDirectoryAndCreateNewFileTest(
    directory: VirtualFile,
    pastedCode: String,
    expectedNewFileName: String
  ): Unit = {
    doPasteToDirectoryWithOutcomeTest(
      directory,
      pastedCode,
      ExpectedPasteTestOutcome.CreateNewFile(expectedNewFileName)
    )
  }

  protected def doPasteToDirectoryAndUpdateExistingFileTest(
    directory: VirtualFile,
    existingFileName: String,
    fileTextBefore: String,
    pastedText: String,
    expectedFileTextAfter: String,
  ): Unit = {
    createOrUpdateFileWithContent(
      directory = directory,
      existingFileName = existingFileName,
      fileText = fileTextBefore
    )

    doPasteToDirectoryWithOutcomeTest(
      directory = directory,
      pastedCode = pastedText,
      expectedAction = AddToExistingFile(fileName = existingFileName, expectedFileContent = expectedFileTextAfter)
    )
  }

  private def doPasteToDirectoryWithOutcomeTest(
    directory: VirtualFile,
    pastedCode: String,
    expectedAction: ExpectedPasteTestOutcome
  ): Unit = {
    val psiDirectory = PsiManager.getInstance(getProject).findDirectory(directory)
    assertNotNull(s"Can't find psi directory for directory ${directory.getPath}", psiDirectory)

    val module = ModuleUtilCore.findModuleForPsiElement(psiDirectory)

    val dataContext = buildTestDataContext(module, psiDirectory, pastedCode)

    // Invoke paste action
    val pasteProvider = new ScalaFilePasteProvider()
    assertTrue(
      "Paste action is not enabled in the context",
      pasteProvider.isPasteEnabled(dataContext)
    )

    val filesBeforePaste = psiDirectory.getVirtualFile.getChildren.toSeq

    inWriteAction {
      pasteProvider.performPaste(dataContext)
    }

    val filesAfterPaste = psiDirectory.getVirtualFile.getChildren.toSeq
    val newFiles = (filesAfterPaste.toSet -- filesBeforePaste.toSet).toSeq

    //We need to save documents to files to test their contents
    // (old and new, because we can create new files and update exising files)
    inWriteAction {
      saveDocumentContentsToDisk(filesAfterPaste)
    }

    expectedAction match {
      case ExpectedPasteTestOutcome.CreateNewFile(expectedFileName) =>
        assertNewFileCreated(newFiles, expectedFileName, pastedCode.withNormalizedSeparator)
      case ExpectedPasteTestOutcome.AddToExistingFile(expectedFileName, expectedFileContent) =>
        assertExistingFileModified(directory, newFiles, expectedFileName, expectedFileContent.withNormalizedSeparator)
    }
  }

  private def assertNewFileCreated(
    newFiles: Seq[VirtualFile],
    expectedFileName: String,
    expectedFileText: String
  ): Unit = {
    val newFileNames = newFiles.map(_.getName)
    assertCollectionEquals(
      "Wrong file names are created after pasting to directory",
      Seq(expectedFileName),
      newFileNames
    )

    val fileContent = new String(newFiles.head.contentsToByteArray())
    assertEquals(
      "Newly created file content should equal to the pasted content",
      expectedFileText,
      fileContent
    )
  }

  private def assertExistingFileModified(
    dir: VirtualFile,
    newFiles: Seq[VirtualFile],
    expectedFileName: String,
    expectedFileTextWithCaret: String
  ): Unit = {
    assertCollectionIsEmpty(
      s"No new files should be created after pasting to directory. Existing file $expectedFileName should be modified instead",
      newFiles.map(_.getName)
    )

    val expectedFile = dir.findChild(expectedFileName)
    assertNotNull(s"Can't find file $expectedFileName in $dir", expectedFile)

    assertExistingFileModified(expectedFile, expectedFileTextWithCaret)
  }

  private def assertExistingFileModified(
    expectedFile: VirtualFile,
    expectedFileTextWithCaret: String
  ): Unit = {
    val (expectedFileText, caretMarkerIdx) = MarkersUtils.extractCaretMarker(expectedFileTextWithCaret, Caret)

    val fileContent = new String(expectedFile.contentsToByteArray())
    assertEquals(
      "Existing file content should equal to the pasted content",
      expectedFileText,
      fileContent
    )

    // Test that editor was opened for the existing file and the caret is located in the right place
    val fileEditorManager = FileEditorManager.getInstance(getProject)
    val editor = fileEditorManager.getSelectedTextEditor
    assertNotNull("Editor should be opened", editor)
    assertEquals("Wrong file is opened in editor", expectedFile, editor.getVirtualFile)

    if (caretMarkerIdx != -1) {
      assertEquals("Wrong caret position", caretMarkerIdx, editor.getCaretModel.getOffset)
    }
  }

  private def buildTestDataContext(
    module: Module,
    psiDirectory: PsiDirectory,
    pastedContent: String
  ): DataContext = {
    val dataContext = SimpleDataContext.builder()
      .add(PlatformCoreDataKeys.MODULE, module)
      .add(LangDataKeys.IDE_VIEW, new IdeView {
        override def getDirectories: Array[PsiDirectory] = Array(psiDirectory)

        override def getOrChooseDirectory(): PsiDirectory = psiDirectory
      })
      .build()

    CopyPasteManager.getInstance.setContents(new TextTransferable(pastedContent))

    dataContext
  }

  private def saveDocumentContentsToDisk(newFiles: Seq[VirtualFile]): Unit = {
    val fileDocumentManager = FileDocumentManager.getInstance
    val newDocuments = newFiles.map(FileDocumentManager.getInstance().getDocument)
    newDocuments.filter(_ != null).foreach(fileDocumentManager.saveDocument)
  }
}

object ScalaFilePasteProviderInSbtProjectTestLike {
  sealed trait ExpectedPasteTestOutcome
  object ExpectedPasteTestOutcome {
    case class CreateNewFile(fileName: String) extends ExpectedPasteTestOutcome
    case class AddToExistingFile(fileName: String, expectedFileContent: String) extends ExpectedPasteTestOutcome
  }

  private object VfsUtils {

    def createOrUpdateFileWithContent(
      directory: VirtualFile,
      existingFileName: String,
      fileText: String
    ): Unit = {
      inWriteAction {
        val file = directory.findOrCreateChildData(this, existingFileName)
        VfsUtil.saveText(file, fileText)
      }
    }
  }
}
