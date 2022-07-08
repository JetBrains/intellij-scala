package org.jetbrains.plugins.scala
package refactoring.rename3

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.refactoring.rename.{RenameProcessor, RenamePsiElementProcessor}
import com.intellij.testFramework.{LightPlatformTestCase, PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.File
import java.nio.file.Path
import java.util
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
abstract class ScalaRenameTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val caretMarker = "/*caret*/"
  private var myEditors: Map[VirtualFile, Editor] = _
  private var myDirectory: VirtualFile = _
  private var filesBefore: Seq[VirtualFile] = _

  protected val folderPath: String = TestUtils.getTestDataPath + "/rename3/"

  private def rootBefore = (folderPath + getTestName(true) + "/before").replace(File.separatorChar, '/')
  private def rootAfter = (folderPath + getTestName(true) + "/after").replace(File.separatorChar, '/')

  override protected def afterSetUpProject(module: Module): Unit = {
    super.afterSetUpProject(module)
    LocalFileSystem.getInstance().refresh(false)
    myDirectory = PsiTestUtil.createTestProjectStructure(projectAdapter, moduleAdapter, rootBefore, new util.HashSet[Path](), true)
    filesBefore =
      VfsUtil.collectChildrenRecursively(myDirectory.findChild("tests")).asScala
        .filter(!_.isDirectory)
        .toSeq
    //hack to avoid pointer leak: if pointer is created early enough it is not considered leak
    filesBefore.foreach(VirtualFilePointerManager.getInstance().create(_, projectAdapter, null))
  }

  protected def doTest(newName: String = "NameAfterRename"): Unit = {
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

  private def findCaretsAndRemoveMarkers(files: Seq[VirtualFile]): Seq[CaretPosition] = {
    val caretsInFile: VirtualFile => Seq[CaretPosition] = { file =>
      var text = fileText(file)
      val fileLength = text.length
      val offsets: Seq[Int] = {
        val builder = Seq.newBuilder[Int]
        val length = caretMarker.length
        var occ = text.indexOf(caretMarker)
        while(occ > 0) {
          builder += occ
          text = text.substring(0, occ) + text.substring(occ + length)
          occ = text.indexOf(caretMarker)
        }

        builder.result()
      }

      val result = offsets.map(offset => CaretPosition(file, offset))
      if (result.nonEmpty) {
        inWriteAction(FileDocumentManager.getInstance().getDocument(file).replaceString(0, fileLength, text))
      }
      result
    }
    files.flatMap(caretsInFile)
  }

  private def createEditors(files: Seq[VirtualFile]): Map[VirtualFile, Editor] = {
    files.iterator.map(f => f -> createEditor(f)).toMap
  }

  protected override def tearDown(): Unit = {
    super.tearDown()
    myEditors = null
    myDirectory = null
    filesBefore = null
    LightPlatformTestCase.closeAndDeleteProject()
  }

  private def projectAdapter = getProjectAdapter
  private def moduleAdapter = getModuleAdapter

  private def doRename(editor: Editor, file: PsiFile, newName: String): String = {
    PsiDocumentManager.getInstance(getProjectAdapter).commitAllDocuments()
    FileDocumentManager.getInstance.saveAllDocuments()

    val element = TargetElementUtil.findTargetElement(
      InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, file): @nowarn("cat=deprecation"),
      TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.ELEMENT_NAME_ACCEPTED)
    assert(element != null, "Reference is not specified.")
    val searchInComments = element.getText != null && element.getText.contains("Comments")
    var oldName: String = ""
    inWriteAction {
      val subst = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, getEditorAdapter)
      if (subst != null) {
        oldName = ScalaNamesUtil.scalaName(subst)
        new RenameProcessor(projectAdapter, subst, newName, searchInComments, false).run()
      }
    }
    val document = PsiDocumentManager.getInstance(getProjectAdapter).getDocument(file)
    PsiDocumentManager.getInstance(getProjectAdapter).doPostponedOperationsAndUnblockDocument(document)
    oldName
  }
}
