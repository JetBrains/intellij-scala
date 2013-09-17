package org.jetbrains.plugins.scala
package refactoring.rename3

import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.util.{ScalaUtils, TestUtils}
import com.intellij.testFramework.{LightPlatformCodeInsightTestCase, PlatformTestUtil, PsiTestUtil}
import java.util
import java.io.File
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.refactoring.rename.{RenameProcessor, RenamePsiElementProcessor}
import com.intellij.openapi.util.text.StringUtil
import scala.collection.mutable.ListBuffer
import com.intellij.psi.{PsiFile, PsiDocumentManager}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Nikolay.Tropin
 * 9/6/13
 */
abstract class ScalaRenameTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val caretMarker = "/*caret*/"

  protected val folderPath: String = TestUtils.getTestDataPath + "/rename3/"

  private def rootBefore = (folderPath + getTestName(true) + "/before").replace(File.separatorChar, '/')
  private def rootAfter = (folderPath + getTestName(true) + "/after").replace(File.separatorChar, '/')

  protected def doTest(newName: String = "NameAfterRename") {
    val dirBefore = PsiTestUtil.createTestProjectStructure(projectAdapter, moduleAdapter, rootBefore, new util.HashSet[File]())
    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
    val filesBefore = dirBefore.findChild("tests").getChildren

    val caretPositions = findCaretsAndRemoveMarkers(filesBefore)
    PsiDocumentManager.getInstance(projectAdapter).commitAllDocuments()
    val editors = createEditors(filesBefore)

    for {
      CaretPosition(vFile, offset) <- caretPositions
    } {
      val file = getPsiManagerAdapter.findFile(vFile)
      val editor = editors(vFile)
      editor.getCaretModel.moveToOffset(offset)

      val oldName = doRename(editor, file, newName)

      val result = vFile.getParent.getParent
      val dirAfter = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootAfter)
      PlatformTestUtil.assertDirectoriesEqual(dirAfter, result, PlatformTestUtil.CVS_FILE_FILTER)

      //rename back for next caret position
      doRename(editor, file, oldName)
    }
    tearDown()
  }

  private def fileText(file: VirtualFile): String = {
    val text = FileDocumentManager.getInstance().getDocument(file).getText
    StringUtil.convertLineSeparators(text)
  }

  case class CaretPosition(file: VirtualFile, offset: Int)

  private def findCaretsAndRemoveMarkers(files: Array[VirtualFile]): Seq[CaretPosition] = {
    val caretsInFile: VirtualFile => Seq[CaretPosition] = { file =>
      val text = fileText(file)
      def findOffsets(s: String): Seq[Int] = {
        val result = ListBuffer[Int]()
        var i = 0
        val length = caretMarker.length
        while(i >= 0 && i < s.length) {
          i = s.indexOf(caretMarker, i)
          if (i >= 0) {
            result += i
            i = i + length
          }
        }
        result.zipWithIndex map {
          case (offset, index) => offset - length * index
        }
      }
      val result = findOffsets(text).map(offset => CaretPosition(file, offset))
      if (result.nonEmpty) {
        val newText = text.replace(caretMarker, "")
        FileDocumentManager.getInstance().getDocument(file).replaceString(0, text.length, newText)
      }
      result
    }
    files.flatMap(caretsInFile)
  }

  private def createEditors(files: Array[VirtualFile]): Map[VirtualFile, Editor] = {
    files.map(f => f -> createEditor(f)).toMap
  }

  private def createEditor(file: VirtualFile) = LightPlatformCodeInsightTestCase.createEditor(file)

  private def projectAdapter = getProjectAdapter
  private def moduleAdapter = getModuleAdapter

  private def doRename(editor: Editor, file: PsiFile, newName: String): String = {
    val element = TargetElementUtilBase.findTargetElement(
      InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file),
      TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase.ELEMENT_NAME_ACCEPTED)
    assert(element != null, "Reference is not specified.")
    val searchInComments = element.getText != null && element.getText.contains("Comments")
    var oldName: String = ""
    ScalaUtils.runWriteAction(new Runnable {
      def run() {
        val subst = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, getEditorAdapter)
        if (subst == null) return
        oldName = ScalaNamesUtil.scalaName(subst)
        new RenameProcessor(projectAdapter, subst, newName, searchInComments, false).run()
      }
    }, projectAdapter, "Test")
    oldName
  }
}
