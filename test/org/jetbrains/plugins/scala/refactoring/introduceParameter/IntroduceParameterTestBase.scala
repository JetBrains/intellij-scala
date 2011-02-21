package org.jetbrains.plugins.scala.refactoring.introduceParameter

import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}
import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.command.undo.UndoManager
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterProcessor
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ide.util.SuperMethodWarningUtil
import com.intellij.psi.{PsiMethod, PsiDocumentManager, PsiManager}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.base.ScalaPsiTestCase

/**
 * @author Alexander Podkhalyuzin
 */

abstract class IntroduceParameterTestBase extends ScalaPsiTestCase {
  override protected def rootPath = super.rootPath + "introduceParameter/"
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"

  protected def doTest = {
    import _root_.junit.framework.Assert._
    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val project = getProject
    val scalaFile: ScalaFile = PsiManager.getInstance(project).
      findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val startOffset = fileText.indexOf(startMarker) + startMarker.length
    assert(startOffset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val fileEditorManager = FileEditorManager.getInstance(project)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, file, startOffset), false)

    var res: String = null

    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    //start to inline
    try {
      ScalaUtils.runWriteActionDoNotRequestConfirmation(new Runnable {
        def run {
          editor.getSelectionModel.setSelection(startOffset, endOffset)
          def invokes() {
            ScalaRefactoringUtil.trimSpacesAndComments(editor, scalaFile)
            PsiDocumentManager.getInstance(project).commitAllDocuments
            val (expr: ScExpression, typez: ScType) = ScalaRefactoringUtil.
              getExpression(project, editor, scalaFile, startOffset, endOffset).get
            val typeText = ScType.presentableText(typez)

            val function = PsiTreeUtil.getContextOfType(expr, true, classOf[ScFunctionDefinition])
            val methodToSearchFor: PsiMethod = SuperMethodWarningUtil.checkSuperMethod(function, RefactoringBundle.message("to.refactor"))

            val occurrences: Array[TextRange] = ScalaRefactoringUtil.getOccurrences(ScalaRefactoringUtil.unparExpr(expr), function)
            val paramName = "param" //todo: test setting
            val replaceAllOccurrences = true //todo: test setting
            val isDefaultParam = false //todo: test setting
            val processor = new ScalaIntroduceParameterProcessor(project, editor, methodToSearchFor, function,
              replaceAllOccurrences, occurrences, startOffset, endOffset, paramName, isDefaultParam, typez, expr)
            processor.run
          }
          ScalaRefactoringUtil.invokeRefactoring(project, editor, scalaFile, null, "Introduce Variable", invokes _)
        }
      }, project, "Test")
      res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim
    }
    catch {
      case e: Exception => assert(false, e.getMessage + "\n" + e.getStackTrace)
    }
    finally {
      ScalaUtils.runWriteAction(new Runnable {
        def run {
          val undoManager = UndoManager.getInstance(project)
          val fileEditor = TextEditorProvider.getInstance.getTextEditor(editor)
          if (undoManager.isUndoAvailable(fileEditor)) {
            undoManager.undo(fileEditor)
          }
          if (undoManager.isUndoAvailable(fileEditor)) {
            undoManager.undo(fileEditor)
          }
        }
      }, project, "Test")
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
    assertEquals(output, res.trim)
  }
}