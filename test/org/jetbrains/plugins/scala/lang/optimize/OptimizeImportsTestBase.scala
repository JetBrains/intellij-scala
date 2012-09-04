package org.jetbrains.plugins.scala
package lang
package optimize


import _root_.com.intellij.psi.impl.source.tree.TreeUtil
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import base.ScalaPsiTestCase
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}

import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.refactoring.inline.GenericInlineHandler
import refactoring.inline.ScalaInlineHandler
import psi.api.base.patterns.ScBindingPattern
import lexer.ScalaTokenTypes
import psi.types.ScType
import psi.api.expr.ScExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiManager
import psi.api.ScalaFile
import java.io.File
import com.intellij.openapi.vfs.LocalFileSystem
import util.ScalaUtils
import settings.ScalaProjectSettings

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.06.2009
 */

abstract class OptimizeImportsTestBase extends ScalaPsiTestCase {

  override def rootPath: String = super.rootPath + "optimize/"

  protected def doTest() {
    import _root_.junit.framework.Assert._

    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]

    val fileEditorManager = FileEditorManager.getInstance(myProject)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, file, 0), false)

    var res: String = null
    var lastPsi = TreeUtil.findLastLeaf(scalaFile.getNode).getPsi


    try {
      if (getTestName(true).startsWith("sorted")) {
        ScalaProjectSettings.getInstance(myProject).setSortImports(true)
      }
      ScalaUtils.runWriteActionDoNotRequestConfirmation(new ScalaImportOptimizer().processFile(scalaFile), myProject, "Test")
      res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim//getImportStatements.map(_.getText()).mkString("\n")
    }
    catch {
      case e: Exception => {
        val z = e
        assert(false, e.getMessage + "\n" + e.getStackTrace)
      }
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
      ScalaProjectSettings.getInstance(myProject).setSortImports(false)
    }

    println("------------------------ " + scalaFile.getName + " ------------------------")
    println(res)
    lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => assertTrue("Test result must be in last comment statement.", false)
    }
    assertEquals(output, res)
  }
}