package org.jetbrains.plugins.scala.refactoring.introduceParameter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaChangeSignatureProcessor, ScalaMethodDescriptor, ScalaParameterInfo}
import org.jetbrains.plugins.scala.lang.refactoring.introduceParameter.ScalaIntroduceParameterHandler
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.{afterExpressionChoosing, trimSpacesAndComments}
import org.jetbrains.plugins.scala.refactoring.refactoringCommonTestDataRoot
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment

import java.io.File

abstract class IntroduceParameterTestBase extends ScalaLightCodeInsightFixtureTestCase {
  protected def folderPath = refactoringCommonTestDataRoot + "introduceParameter/"
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"
  private val allMarker = "//all = "
  private val nameMarker = "//name = "
  private val defaultMarker = "//default = "
  private val constructorMarker = "//constructor = "

  protected def doTest(): Unit = {
    import _root_.org.junit.Assert._
    implicit val project: Project = getProject
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileText(getTestName(false) + ".scala", fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val startOffset = fileText.indexOf(startMarker) + startMarker.length
    assert(startOffset != -1 + startMarker.length,
      "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    implicit val editor: Editor = openEditorAtOffset(startOffset)

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
      executeWriteActionCommand("Test") {
        editor.getSelectionModel.setSelection(startOffset, endOffset)
        afterExpressionChoosing(scalaFile, "Introduce Variable") {
          trimSpacesAndComments(editor, scalaFile)
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          val handler = new ScalaIntroduceParameterHandler()
          val (exprWithTypes, elems) = handler.selectedElementsInFile(scalaFile).getOrElse(return)

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
          val changeInfo = ScalaChangeInfo(descriptor.getVisibility, data.methodToSearchFor, descriptor.getName, returnType,
            descriptor.parameters, isDefaultParam)

          changeInfo.introducedParameterData = Some(data)
          new ScalaChangeSignatureProcessor(changeInfo).run()
        }
      }
    }
    catch {
      case e: Exception =>
        throw new AssertionError(e)
    }

    val ExpectedResultFromLastComment(res, output) = TestUtils.extractExpectedResultFromLastComment(getFile)

    assertEquals(output, res.trim)
  }
}