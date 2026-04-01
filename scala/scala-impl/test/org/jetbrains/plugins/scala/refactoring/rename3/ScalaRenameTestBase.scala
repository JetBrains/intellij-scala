package org.jetbrains.plugins.scala.refactoring.rename3

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.refactoring.refactoringCommonTestDataRoot
import org.jetbrains.plugins.scala.util.WriteCommandActionEx

import java.nio.file.Path
import java.util
import scala.jdk.CollectionConverters._

abstract class ScalaRenameTestBase extends ScalaLightCodeInsightFixtureTestCase {

  protected val caretMarker = "/*caret*/"

  private var myEditors: Map[VirtualFile, Editor] = _
  private var myDirectory: VirtualFile = _
  private var filesBefore: Seq[VirtualFile] = _

  protected val folderPath: Path = refactoringCommonTestDataRoot / "rename3"

  private def rootBefore: Path = folderPath / getTestName(true) / "before"

  private def rootAfter: Path = folderPath / getTestName(true) / "after"

  protected def doTest(newName: String = "NameAfterRename", withAutoRenames: Boolean = false): Unit = {
    val caretPositions = findCaretsAndRemoveMarkers(filesBefore)
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    myEditors = createEditors(filesBefore)

    assertThat("No caret positions found in test case. Use `/*caret*/`.", caretPositions.nonEmpty)

    for {
      CaretPosition(vFile, offset) <- caretPositions
    } {
      val file = getPsiManager.findFile(vFile)
      val editor = myEditors(vFile)
      editor.getCaretModel.moveToOffset(offset)

      val oldName = doRename(editor, file, newName, withAutoRenames)

      val dirAfter = LocalFileSystem.getInstance.refreshAndFindFileByNioFile(rootAfter)
      PlatformTestUtil.assertDirectoriesEqual(dirAfter, myDirectory)

      //rename back for next caret position
      doRename(editor, file, oldName, withAutoRenames)
    }
  }

  private def fileText(file: VirtualFile): String = {
    val text = FileDocumentManager.getInstance().getDocument(file).getText
    StringUtil.convertLineSeparators(text)
  }

  case class CaretPosition(file: VirtualFile, offset: Int)

  private def findCaretsAndRemoveMarkers(files: Seq[VirtualFile]): Seq[CaretPosition] = {
    def caretsInFile(file: VirtualFile): Seq[CaretPosition] = {
      var text = fileText(file)
      val fileLength = text.length
      val offsets: Seq[Int] = {
        val builder = Seq.newBuilder[Int]
        val length = caretMarker.length
        var occ = text.indexOf(caretMarker)
        while (occ > 0) {
          builder += occ
          text = text.substring(0, occ) + text.substring(occ + length)
          occ = text.indexOf(caretMarker)
        }

        builder.result()
      }

      val result = offsets.map(offset => CaretPosition(file, offset))
      if (result.nonEmpty) {
        WriteCommandActionEx.runWriteCommandAction(getProject, () => {
          FileDocumentManager.getInstance().getDocument(file).replaceString(0, fileLength, text)
        })
      }
      result
    }

    files.flatMap(caretsInFile)
  }

  private def createEditors(files: Seq[VirtualFile]): Map[VirtualFile, Editor] = {
    def createEditor(file: VirtualFile): Editor = {
      myFixture.openFileInEditor(file)
      myFixture.getEditor
    }

    files.iterator.map(f => f -> createEditor(f)).toMap
  }

  override protected def setUp(): Unit = {
    super.setUp()
    LocalFileSystem.getInstance().refresh(false)
    myDirectory = PsiTestUtil.createTestProjectStructure(getProject, getModule, rootBefore.toString, new util.HashSet[Path](), true)
    filesBefore =
      VfsUtil.collectChildrenRecursively(myDirectory.findChild("tests")).asScala
        .filter(!_.isDirectory)
        .toSeq
  }

  private def doRename(editor: Editor, file: PsiFile, newName: String, withAutoRenames: Boolean): String = {
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()
    FileDocumentManager.getInstance.saveAllDocuments()

    val element = TargetElementUtil.findTargetElement(
      InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file),
      TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.ELEMENT_NAME_ACCEPTED)
    assert(element != null, "Reference is not specified.")
    val searchInComments = element.getText != null && element.getText.contains("Comments")
    var oldName: String = ""

    val subst = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, getEditor)
    if (subst != null) {
      oldName = ScalaNamesUtil.scalaName(subst)
      val renameRefactoring = RefactoringFactory.getInstance(getProject).createRename(subst, newName, searchInComments, false)
      if (withAutoRenames) {
        renameRefactoring.respectEnabledAutomaticRenames()
      }
      renameRefactoring.run()
    }

    val document = PsiDocumentManager.getInstance(getProject).getDocument(file)
    PsiDocumentManager.getInstance(getProject).doPostponedOperationsAndUnblockDocument(document)
    oldName
  }
}
