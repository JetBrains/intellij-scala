package org.jetbrains.plugins.scala
package refactoring.introduceField

import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import java.io.File
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import junit.framework.Assert._
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.refactoring.introduceField.{IntroduceFieldSettings, IntroduceFieldContext, ScalaIntroduceFieldFromExpressionHandler}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.StdType
import org.jetbrains.plugins.scala.util.ScalaUtils

/**
 * Nikolay.Tropin
 * 7/17/13
 */
abstract class IntroduceFieldTestBase() extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"
  private val replaceAllMarker = "/*replaceAll*/"
  private val initInDeclarationMarker = "/*initInDeclaration*/"
  private val initLocallyMarker = "/*initLocally*/"
  private val selectedClassNumberMarker = "/*selectedClassNumber = "

  def folderPath: String = baseRootPath() + "introduceField/"

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

    val editor = getEditorAdapter
    editor.getSelectionModel.setSelection(startOffset, endOffset)

    var res: String = null

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    val replaceAll = fileText.contains(replaceAllMarker)
    val initInDecl = if (fileText.contains(initInDeclarationMarker)) Some(true)
    else if (fileText.contains(initLocallyMarker)) Some(false)
    else None
    val selectedClassNumber = fileText.indexOf(selectedClassNumberMarker) match {
      case -1 => 0
      case idx: Int => fileText.charAt(idx + selectedClassNumberMarker.length).toString.toInt
    }

    //start to inline
    try {
      val handler = new ScalaIntroduceFieldFromExpressionHandler
      val Some((expr, types)) = ScalaRefactoringUtil.getExpression(getProjectAdapter, editor, scalaFile, startOffset, endOffset)
      val aClass = expr.parents.toList.filter(_.isInstanceOf[ScTemplateDefinition])(selectedClassNumber).asInstanceOf[ScTemplateDefinition]
      val ifc = new IntroduceFieldContext[ScExpression](getProjectAdapter, editor, scalaFile, expr, types, aClass)
      val settings = new IntroduceFieldSettings[ScExpression](ifc)
      settings.replaceAll = replaceAll
      initInDecl.foreach(settings.initInDeclaration = _)
      settings.defineVar = true
      settings.name = "i"
      settings.explicitType = true
      settings.scType = StdType.QualNameToType("scala.Int")
      ScalaUtils.runWriteActionDoNotRequestConfirmation(new Runnable {
        def run() {handler.runRefactoring(ifc, settings)}
      }, getProjectAdapter, "Test")
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
