package org.jetbrains.plugins.scala.refactoring.introduceParameter

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import java.io.File
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}
import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterProcessor
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.psi.{PsiMethod, PsiDocumentManager}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.io.FileUtil

/**
 * @author Alexander Podkhalyuzin
 */

abstract class IntroduceParameterTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  protected def folderPath = baseRootPath() + "introduceParameter/"
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"
  private val allMarker = "//all = "
  private val nameMarker = "//name = "
  private val defaultMarker = "//default = "

  protected def doTest() {
    import _root_.junit.framework.Assert._
    val project = getProjectAdapter
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val startOffset = fileText.indexOf(startMarker) + startMarker.length
    assert(startOffset != -1 + startMarker.length,
      "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val fileEditorManager = FileEditorManager.getInstance(project)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, file, startOffset), false)

    var res: String = null

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    //getting settings
    val allOffset = fileText.indexOf(allMarker)
    val replaceAllOccurrences = if (allOffset == -1) true else {
      val comment = scalaFile.findElementAt(allOffset)
      val commentText = comment.getText
      val text = commentText.substring(allMarker.length)
      text match {
        case "true" => true
        case "false" => false
      }
    }

    val nameOffset = fileText.indexOf(nameMarker)
    val paramName = if (nameOffset == -1) "param" else {
      val comment = scalaFile.findElementAt(nameOffset)
      val commentText = comment.getText
      commentText.substring(nameMarker.length)
    }

    val defaultOffset = fileText.indexOf(defaultMarker)
    val isDefaultParam = if (defaultOffset == -1) false else {
      val comment = scalaFile.findElementAt(defaultOffset)
      val commentText = comment.getText
      commentText.substring(defaultMarker.length) match {
        case "true" => true
        case "false" => false
      }
    }

    //start to inline
    try {
      ScalaUtils.runWriteActionDoNotRequestConfirmation(new Runnable {
        def run() {
          editor.getSelectionModel.setSelection(startOffset, endOffset)
          ScalaRefactoringUtil.afterExpressionChoosing(project, editor, scalaFile, null, "Introduce Variable") {
            ScalaRefactoringUtil.trimSpacesAndComments(editor, scalaFile)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val (expr: ScExpression, types: Array[ScType]) =
              ScalaRefactoringUtil.getExpression(project, editor, scalaFile, startOffset, endOffset).get

            val function = PsiTreeUtil.getContextOfType(expr, true, classOf[ScFunctionDefinition])
            val methodToSearchFor: PsiMethod = SuperMethodWarningUtil.checkSuperMethod(function, RefactoringBundle.message("to.refactor"))

            val occurrences: Array[TextRange] = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr),
              function.body.getOrElse(function))
            val processor = new ScalaIntroduceParameterProcessor(project, editor, methodToSearchFor, function,
              replaceAllOccurrences, occurrences, startOffset, endOffset, paramName, isDefaultParam, types(0), expr)
            processor.run()
          }
        }
      }, project, "Test")
      res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim
    }
    catch {
      case e: Exception => assert(assertion = false, message = e.getMessage + "\n" + e.getStackTrace)
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
    assertEquals(output, res.trim)
  }
}