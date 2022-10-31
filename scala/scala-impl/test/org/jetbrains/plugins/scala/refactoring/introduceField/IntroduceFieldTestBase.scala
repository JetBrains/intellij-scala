package org.jetbrains.plugins.scala.refactoring.introduceField

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Int
import org.jetbrains.plugins.scala.lang.refactoring.introduceField.{IntroduceFieldContext, IntroduceFieldSettings, ScalaIntroduceFieldFromExpressionHandler}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getExpressionWithTypes
import org.jetbrains.plugins.scala.refactoring.refactoringCommonTestDataRoot
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.junit.Assert._

import java.io.File

abstract class IntroduceFieldTestBase() extends ScalaLightCodeInsightFixtureTestCase {
  private val startMarker = "/*start*/"
  private val endMarker = "/*end*/"
  private val replaceAllMarker = "/*replaceAll*/"
  private val initInDeclarationMarker = "/*initInDeclaration*/"
  private val initLocallyMarker = "/*initLocally*/"
  private val selectedClassNumberMarker = "/*selectedClassNumber = "

  protected def folderPath: String = refactoringCommonTestDataRoot + "introduceField/"

  implicit protected def projectContext: Project = getProject

  protected def doTest(scType: ScType = Int): Unit = {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    var fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))

    val startOffset = fileText.indexOf(startMarker)
    assert(startOffset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    fileText = fileText.replace(startMarker, "")

    val endOffset = fileText.indexOf(endMarker)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")
    fileText = fileText.replace(endMarker, "")

    configureFromFileText(getTestName(false) + ".scala", fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val editor = getEditor
    editor.getSelectionModel.setSelection(startOffset, endOffset)

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
      val Some((expr, types)) = getExpressionWithTypes(scalaFile, editor.getDocument, startOffset, endOffset)(getProject)
      val aClass = expr.parents.toList.filter(_.isInstanceOf[ScTemplateDefinition])(selectedClassNumber).asInstanceOf[ScTemplateDefinition]
      val ifc = new IntroduceFieldContext[ScExpression](getProject, editor, scalaFile, expr, types, aClass)
      val settings = new IntroduceFieldSettings[ScExpression](ifc)
      settings.replaceAll = replaceAll
      initInDecl.foreach(settings.initInDeclaration = _)
      settings.defineVar = true
      settings.name = "i"
      settings.scType = scType

      executeWriteActionCommand("Test", UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION) {
        handler.runRefactoring(ifc, settings)
        UsefulTestCase.doPostponedFormatting(getProject)
      }
    } catch {
      case e: Exception =>
        throw new AssertionError(e)
    }

    val ExpectedResultFromLastComment(res, output) = TestUtils.extractExpectedResultFromLastComment(getFile)

    assertEquals(output, res)
  }
}
