package org.jetbrains.plugins.scala
package refactoring.extractMethod

import java.io.File

import _root_.com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import _root_.org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import _root_.org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import _root_.org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodHandler
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert._

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.04.2010
 */

abstract class ScalaExtractMethodTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"
  private val scopeMarker = "/*inThisScope*/"

  def folderPath: String = baseRootPath + "extractMethod/"

  protected def doTest(settings: ScalaCodeStyleSettings
                       = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter))) {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    var fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))

    val scopeOffset = fileText.indexOf(scopeMarker) match {
      case -1 => null
      case other => other
    }

    if (scopeOffset != null)
      fileText = fileText.replace(scopeMarker, "")

    val startOffset = fileText.indexOf(startMarker)
    assert(startOffset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    fileText = fileText.replace(startMarker, "")

    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")
    fileText = fileText.replace(endMarker, "")


    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]

    val fileEditorManager = FileEditorManager.getInstance(getProjectAdapter)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProjectAdapter, getVFileAdapter, startOffset), false)
    editor.getSelectionModel.setSelection(startOffset, endOffset)

    var res: String = null

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    val oldSettings = ScalaCodeStyleSettings.getInstance(getProjectAdapter).clone()
    TypeAnnotationSettings.set(getProjectAdapter, settings)

    //start to inline
    try {
      val handler = new ScalaExtractMethodHandler
      val context = SimpleDataContext.getSimpleContext("chosenTargetScope", scopeOffset)
      handler.invoke(getProjectAdapter, getEditorAdapter, scalaFile, context)
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
      case _ =>
        assertTrue("Test result must be in last comment statement.", false)
        ""
    }
    
    TypeAnnotationSettings.set(getProjectAdapter, oldSettings.asInstanceOf[ScalaCodeStyleSettings])
    assertEquals(output, res)
  }
}