package org.jetbrains.plugins.scala.refactoring.extractMethod

import _root_.com.intellij.openapi.command.undo.UndoManager
import _root_.com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import _root_.com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import _root_.org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import _root_.org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodHandler
import _root_.org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import java.io.File
import _root_.org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import _root_.org.jetbrains.plugins.scala.base.ScalaPsiTestCase
import junit.framework.Assert._

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.04.2010
 */

abstract class ScalaExtractMethodTestBase extends ScalaPsiTestCase {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"

  override def rootPath: String = super.rootPath + "extractMethod/"

  protected def doTest = {
    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(startMarker)
    val startOffset = offset + startMarker.length

    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val fileEditorManager = FileEditorManager.getInstance(myProject)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, file, offset), false)
    editor.getSelectionModel.setSelection(startOffset, endOffset)

    var res: String = null

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    //start to inline
    try {
      val handler = new ScalaExtractMethodHandler
      handler.invoke(myProject, editor, scalaFile, null)
      res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim
    }
    catch {
      case e: Exception => assert(false, e.getMessage + "\n" + e.getStackTrace.map(_.toString).mkString("  \n"))
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