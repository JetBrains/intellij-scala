package org.jetbrains.plugins.scala.lang.autoImport

import _root_.org.jetbrains.plugins.scala.codeInspection.importInspections.ScalaAddImportPass
import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.{TextEditorImpl, PsiAwareTextEditorImpl, TextEditorProvider}
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.{PsiManager, PsiClass}
import java.io.File
import psi.api.base.ScReferenceElement
import psi.api.ScalaFile
import com.intellij.psi.util.PsiTreeUtil
import psi.api.expr.ScExpression
import base.ScalaPsiTestCase
import lexer.ScalaTokenTypes
import util.ScalaUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.03.2009
 */

abstract class AutoImportTestBase extends ScalaPsiTestCase {
  private val refMarker = "/*ref*/"

  override protected def rootPath = super.rootPath + "autoImport/"


  protected def doTest: Unit = {
    import _root_.junit.framework.Assert._
    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(refMarker)
    val refOffset = offset + refMarker.length
    assert(offset != -1, "Not specified ref marker in test case. Use /*ref*/ in scala file for this.")
    val ref: ScReferenceElement = PsiTreeUtil.
            getParentOfType(scalaFile.findElementAt(refOffset), classOf[ScReferenceElement])
    assert(ref != null, "Not specified reference at marker.")

    ref.resolve match {
      case null =>
      case _ => assert(false, "Reference must be unresolved.")
    }

    val cache = new ScalaShortNamesCacheImpl(myProject)
    cache.runStartupActivity

    val classes = ScalaAddImportPass.getClasses(ref, myProject)
    assert(classes.length > 0, "Haven't classes to import")
    val fileEditorManager = FileEditorManager.getInstance(myProject)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, file, offset), false)

    var res: String = null

    try {
      ScalaUtils.runWriteAction(new Runnable {
        def run {
          scalaFile.addImportForClass(classes(0))
        }
      }, myProject, "Test")
      res = scalaFile.getImportStatements.map(_.getText()).mkString("\n")
      assert(ref.resolve != null, "reference is unresolved after import action")
    }
    catch {
      case e: Exception => assert(false, e.getMessage + "\n" + e.getStackTrace)
    }
    finally {
      ScalaUtils.runWriteAction(new Runnable {
        def run {
          val undoManager = UndoManager.getInstance(myProject)
          val fileEditor = TextEditorProvider.getInstance.getTextEditor(editor)
          if (undoManager.isUndoAvailable(fileEditor)) {
            undoManager.undo(fileEditor)
          }
        }
      }, myProject, "Test")
    }




    println("------------------------ " + scalaFile.getName + " ------------------------")
    println(res)
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
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
    assertEquals(output, res.trim)
  }
}