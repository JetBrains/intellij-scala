package org.jetbrains.plugins.scala.refactoring.introduceParameter

import java.io.File

import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaChangeSignatureProcessor, ScalaMethodDescriptor, ScalaParameterInfo}
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterHandler
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.util.ScalaUtils

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
  private val constructorMarker = "//constructor = "

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
    def getSetting(marker: String, default: String): String = {
      val offset = fileText.indexOf(marker)
      if (offset == -1) default
      else {
        val comment = scalaFile.findElementAt(offset)
        comment.getText.substring(marker.length)
      }
    }
    val replaceAllOccurrences = getSetting(allMarker, "true").toBoolean
    val paramName = getSetting(nameMarker, "param")
    val isDefaultParam = getSetting(defaultMarker, "false").toBoolean
    val toPrimaryConstructor = getSetting(constructorMarker, "false").toBoolean

    //start to inline
    try {
      ScalaUtils.runWriteActionDoNotRequestConfirmation(new Runnable {
        def run() {
          editor.getSelectionModel.setSelection(startOffset, endOffset)
          ScalaRefactoringUtil.afterExpressionChoosing(project, editor, scalaFile, null, "Introduce Variable") {
            ScalaRefactoringUtil.trimSpacesAndComments(editor, scalaFile)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val handler = new ScalaIntroduceParameterHandler()
            val (exprWithTypes, elems) = handler.selectedElements(scalaFile, project, editor) match {
              case Some((x, y)) => (x, y)
              case None => return
            }

            val (methodLike: ScMethodLike, returnType) =
              if (toPrimaryConstructor)
                (PsiTreeUtil.getContextOfType(elems.head, true, classOf[ScClass]).constructor.get, Any)
              else {
                val fun = PsiTreeUtil.getContextOfType(elems.head, true, classOf[ScFunctionDefinition])
                (fun, fun.returnType.getOrAny)
              }
            val collectedData = handler.collectData(exprWithTypes, elems, methodLike, editor)
            assert(collectedData.isDefined, "Could not collect data for introduce parameter")
            val data = collectedData.get.copy(paramName = paramName, replaceAll = replaceAllOccurrences)

            val paramInfo = new ScalaParameterInfo(data.paramName, -1, data.tp, project, false, false, data.defaultArg, isIntroducedParameter = true)
            val descriptor: ScalaMethodDescriptor = handler.createMethodDescriptor(data.methodToSearchFor, paramInfo)
            val changeInfo = new ScalaChangeInfo(descriptor.getVisibility, data.methodToSearchFor, descriptor.getName, returnType,
              descriptor.parameters, isDefaultParam)

            changeInfo.introducedParameterData = Some(data)
            new ScalaChangeSignatureProcessor(project, changeInfo).run()
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
      case _ =>
        assertTrue("Test result must be in last comment statement.", false)
        ""
    }
    assertEquals(output, res.trim)
  }
}