package org.jetbrains.plugins.scala.refactoring
package changeSignature

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi._
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.changeSignature._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaChangeSignatureProcessor, ScalaParameterInfo}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.annotations._
import org.jetbrains.plugins.scala.util._
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert._
import org.junit.runner.RunWith

import java.io.File

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
abstract class ChangeSignatureTestBase extends ScalaLightCodeInsightFixtureTestCase {

  protected var targetMethod: PsiMember = null
  protected var isAddDefaultValue = false

  implicit def projectContext: ProjectContext = getProject

  override def getTestDataPath: String = folderPath

  def folderPath: String = refactoringCommonTestDataRoot

  def mainFileName(testName: String): String
  def mainFileAfterName(testName: String): String
  def secondFileName(testName: String): String
  def secondFileAfterName(testName: String): String

  def processor(newVisibility: String,
                newName: String,
                newReturnType: String,
                newParams: => Seq[Seq[ParameterInfo]]): ChangeSignatureProcessorBase

  def findTargetElement: PsiMember

  protected def doTest(newVisibility: String,
                       newName: String,
                       newReturnType: String,
                       newParams: => Seq[Seq[ParameterInfo]],
                       settings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))): Unit = {
    val testName = getTestName(false)

    val oldSettings = ScalaCodeStyleSettings.getInstance(getProject).clone()
    TypeAnnotationSettings.set(getProject, settings)

    val secondName = secondFileName(testName)
    val checkSecond = secondName != null

    val secondFile = if (checkSecond) {
      val secondFileText = getTextFromTestData(secondName)
      addFileToProject(secondName, secondFileText)
    } else null

    val fileName = mainFileName(testName)
    myFixture.configureByFile(fileName)
    targetMethod = findTargetElement

    processor(newVisibility, newName, newReturnType, newParams).run()

    PostprocessReformattingAspect.getInstance(getProject).doPostponedFormatting()

    val mainAfterText = getTextFromTestData(mainFileAfterName(testName))
    
    TypeAnnotationSettings.set(getProject, oldSettings.asInstanceOf[ScalaCodeStyleSettings])
    assertEquals(mainAfterText, getFile.getText)

    if (checkSecond) {
      val secondAfterText = getTextFromTestData(secondFileAfterName(testName))
      assertEquals(secondAfterText, secondFile.getText)
    }
  }

  protected def addFileToProject(fileName: String, text: String): PsiFile =
    PsiFileTestUtil.addFileToProject(fileName, text, getProject)

  protected def getTextFromTestData(fileName: String) = {
    val file = new File(getTestDataPath + fileName)
    FileUtilRt.loadFile(file, CharsetToolkit.UTF8, true)
  }

  protected def getPsiTypeFromText(typeText: String, context: PsiElement): PsiType = {
    val factory: JavaCodeFragmentFactory = JavaCodeFragmentFactory.getInstance(getProject)
    factory.createTypeCodeFragment(typeText, context, false).getType
  }

  protected def javaProcessor(newVisibility: String,
                              newName: String,
                              newReturnType: String,
                              newParams: => Seq[Seq[ParameterInfo]]): ChangeSignatureProcessorBase = {

    val psiMethod = targetMethod.asInstanceOf[PsiMethod]
    val retType =
      if (newReturnType != null) getPsiTypeFromText(newReturnType, psiMethod) else psiMethod.getReturnType

    val params = newParams.flatten.map(_.asInstanceOf[ParameterInfoImpl]).toArray

    new ChangeSignatureProcessor(getProject, psiMethod, /*generateDelegate = */ false,
      newVisibility, newName, retType, params, Array.empty)
  }

  protected def scalaProcessor(newVisibility: String,
                               newName: String,
                               newReturnType: String,
                               newParams: => Seq[Seq[ParameterInfo]],
                               isAddDefaultValue: Boolean): ChangeSignatureProcessorBase = {
    val maybeReturnType = targetMethod match {
      case fun: ScFunction =>
        Option(newReturnType).flatMap {
          createTypeFromText(_, fun, fun)
        }.orElse {
          fun.returnType.toOption
        }
      case _ => None
    }

    val params = newParams.map(_.map(_.asInstanceOf[ScalaParameterInfo]))
    // TODO Having this repeated separately somehow defies the purpose of testing
    val annotationNeeded = ScalaTypeAnnotationSettings(targetMethod.getProject).isTypeAnnotationRequiredFor(
      Declaration(targetMethod, Visibility(newVisibility)), Location(targetMethod), Some(Definition(targetMethod)))

    val changeInfo =
      ScalaChangeInfo(newVisibility, targetMethod.asInstanceOf[ScMethodLike], newName, maybeReturnType.getOrElse(Any), params,
        isAddDefaultValue, Some(annotationNeeded))

    new ScalaChangeSignatureProcessor(changeInfo)(getProject)
  }
}
