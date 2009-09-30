package org.jetbrains.plugins.scala.refactoring.rename

import org.jetbrains.plugins.scala.base.ScalaPsiTestCase
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.command.undo.UndoManager
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import org.jetbrains.plugins.scala.lang.refactoring.inline.ScalaInlineHandler
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.inline.GenericInlineHandler
import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.09.2009
 */

class ScalaRenameTestBase extends ScalaPsiTestCase {
  id : ScalaPsiTestCase =>
  val caretMarker = "/*caret*/"

  override protected def rootPath = super.rootPath + "rename/"

  protected def doTest: Unit = {
    import _root_.junit.framework.Assert._
    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(caretMarker) + caretMarker.length + 1
    assert(offset != -1, "Not specified caret marker in test case. Use /*caret*/ in scala file for this.")
    val element = scalaFile.findElementAt(offset).getParent
    assert(element.isInstanceOf[ScReferenceElement], "Reference is not specified.")
    val resolve = element.asInstanceOf[ScReferenceElement].resolve
    assert(resolve != null, "Cannot resolve Symbol")
    val fileEditorManager = FileEditorManager.getInstance(myProject)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, file, offset), false)

    var res: String = null

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    //start to inline
    try {
      ScalaUtils.runWriteAction(new Runnable {
        def run {
          new RenameProcessor(resolve.getProject, resolve, "NameAfterRename", false, false).run()
        }
      }, resolve.getProject, "Test")
      res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim
    }
    catch {
      case e: Exception => assert(false, e.getMessage + "\n" + e.getStackTrace)
    }
    finally {
      ScalaUtils.runWriteAction(new Runnable {
        def run {
          val undoManager = UndoManager.getInstance(getProject)
          val fileEditor = TextEditorProvider.getInstance.getTextEditor(editor)
          if (undoManager.isUndoAvailable(fileEditor)) {
            undoManager.undo(fileEditor)
          }
        }
      }, myProject, "Test")
    }

    println("------------------------ " + scalaFile.getName + " ------------------------")
    println(res)

    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => {
        assertTrue("Test result must be in last comment statement.", false)
        ""
      }
    }
    assertEquals(output, res)
  }
}