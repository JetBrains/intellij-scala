package org.jetbrains.plugins.scala
package refactoring.rename3

import java.io.File
import java.util

import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.refactoring.rename.{RenameProcessor, RenamePsiElementProcessor}
import com.intellij.testFramework.{LightPlatformCodeInsightTestCase, LightPlatformTestCase, PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.mutable.ListBuffer

/**
 * Nikolay.Tropin
 * 9/6/13
 */
abstract class ScalaRenameTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val caretMarker = "/*caret*/"
  private var myEditors: Map[VirtualFile, Editor] = null
  private var myDirectory: VirtualFile = null

  protected val folderPath: String = TestUtils.getTestDataPath + "/rename3/"

  private def rootBefore = (folderPath + getTestName(true) + "/before").replace(File.separatorChar, '/')
  private def rootAfter = (folderPath + getTestName(true) + "/after").replace(File.separatorChar, '/')

  protected def doTest(newName: String = "NameAfterRename") {
    myDirectory = PsiTestUtil.createTestProjectStructure(projectAdapter, moduleAdapter, rootBefore, new util.HashSet[File]())
    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
    val filesBefore = myDirectory.findChild("tests").getChildren

    val caretPositions = findCaretsAndRemoveMarkers(filesBefore)
    PsiDocumentManager.getInstance(projectAdapter).commitAllDocuments()
    myEditors = createEditors(filesBefore)

    for {
      CaretPosition(vFile, offset) <- caretPositions
    } {
      val file = getPsiManagerAdapter.findFile(vFile)
      val editor = myEditors(vFile)
      editor.getCaretModel.moveToOffset(offset)

      val oldName = doRename(editor, file, newName)

      val dirAfter = LocalFileSystem.getInstance.refreshAndFindFileByPath(rootAfter)
      PlatformTestUtil.assertDirectoriesEqual(dirAfter, myDirectory)

      //rename back for next caret position
      doRename(editor, file, oldName)
    }
  }

  private def fileText(file: VirtualFile): String = {
    val text = FileDocumentManager.getInstance().getDocument(file).getText
    StringUtil.convertLineSeparators(text)
  }

  case class CaretPosition(file: VirtualFile, offset: Int)

  private def findCaretsAndRemoveMarkers(files: Array[VirtualFile]): Seq[CaretPosition] = {
    val caretsInFile: VirtualFile => Seq[CaretPosition] = { file =>
      var text = fileText(file)
      val fileLength = text.length
      def findOffsets(s: String): Seq[Int] = {
        val result = ListBuffer[Int]()
        val length = caretMarker.length
        var occ = text.indexOf(caretMarker)
        while(occ > 0) {
          result += occ
          text = text.substring(0, occ) + text.substring(occ + length)
          occ = text.indexOf(caretMarker)
        }

        result
      }
      val result = findOffsets(text).map(offset => CaretPosition(file, offset))
      if (result.nonEmpty) {
        FileDocumentManager.getInstance().getDocument(file).replaceString(0, fileLength, text)
      }
      result
    }
    files.flatMap(caretsInFile)
  }

  private def createEditors(files: Array[VirtualFile]): Map[VirtualFile, Editor] = {
    files.map(f => f -> createEditor(f)).toMap
  }

  private def createEditor(file: VirtualFile) = {
    LightPlatformCodeInsightTestCase.createEditor(file)
  }


  protected override def tearDown() {
    super.tearDown()
    extensions.inWriteAction(LightPlatformTestCase.closeAndDeleteProject())
  }

  private def projectAdapter = getProjectAdapter
  private def moduleAdapter = getModuleAdapter

  private def doRename(editor: Editor, file: PsiFile, newName: String): String = {
    val element = TargetElementUtilBase.findTargetElement(
      InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file),
      TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase.ELEMENT_NAME_ACCEPTED)
    assert(element != null, "Reference is not specified.")
    val searchInComments = element.getText != null && element.getText.contains("Comments")
    var oldName: String = ""
    extensions.inWriteAction {
      val subst = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, getEditorAdapter)
      if (subst != null) {
        oldName = ScalaNamesUtil.scalaName(subst)
        new RenameProcessor(projectAdapter, subst, newName, searchInComments, false).run()
      }
    }
    PsiDocumentManager.getInstance(getProjectAdapter).commitAllDocuments()
    FileDocumentManager.getInstance.saveAllDocuments()
    oldName
  }
}
