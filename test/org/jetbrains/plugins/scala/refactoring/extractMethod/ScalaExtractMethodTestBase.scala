package org.jetbrains.plugins.scala
package refactoring.extractMethod

import _root_.com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import _root_.org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import _root_.org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodHandler
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import java.io.File
import _root_.org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import base.ScalaLightPlatformCodeInsightTestCaseAdapter
import com.intellij.testFramework.UsefulTestCase
import junit.framework.Assert._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.io.FileUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.04.2010
 */

abstract class ScalaExtractMethodTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"

  def folderPath: String = baseRootPath() + "extractMethod/"

  protected def doTest() {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(startMarker)
    val startOffset = offset + startMarker.length

    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val fileEditorManager = FileEditorManager.getInstance(getProjectAdapter)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProjectAdapter, file, offset), false)
    editor.getSelectionModel.setSelection(startOffset, endOffset)

    var res: String = null

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    //start to inline
    try {
      val handler = new ScalaExtractMethodHandler
      handler.invoke(getProjectAdapter, editor, scalaFile, null)
      UsefulTestCase.doPostponedFormatting(getProjectAdapter)
      res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim
    }
    catch {
      case e: Exception => assert(assertion = false, message = e.getMessage + "\n" + e.getStackTrace.map(_.toString).mkString("  \n"))
    }

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