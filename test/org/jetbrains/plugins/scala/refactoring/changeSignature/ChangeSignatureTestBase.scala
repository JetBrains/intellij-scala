package org.jetbrains.plugins.scala
package refactoring.changeSignature

import java.io.File

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.{CharsetToolkit, VfsUtil}
import com.intellij.psi._
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.changeSignature._
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.{ScalaChangeSignatureProcessor, ScalaParameterInfo}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.{TypeAnnotationSettings, TypeAnnotationUtil}
import org.junit.Assert._

/**
 * Nikolay.Tropin
 * 2014-08-14
 */
abstract class ChangeSignatureTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  var targetMethod: PsiMember = null
  protected var isAddDefaultValue = false

  implicit def projectContext: ProjectContext = getProjectAdapter

  override def getTestDataPath = folderPath

  def folderPath: String

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
                       settings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter))) {
    val testName = getTestName(false)

    val oldSettings = ScalaCodeStyleSettings.getInstance(getProjectAdapter).clone()
    TypeAnnotationSettings.set(getProjectAdapter, settings)

    val secondName = secondFileName(testName)
    val checkSecond = secondName != null

    val secondFile = if (checkSecond) {
      val secondFileText = getTextFromTestData(secondName)
      addFileToProject(secondName, secondFileText)
    } else null

    val fileName = mainFileName(testName)
    configureByFile(fileName)
    targetMethod = findTargetElement

    processor(newVisibility, newName, newReturnType, newParams).run()

    PostprocessReformattingAspect.getInstance(getProjectAdapter).doPostponedFormatting()

    val mainAfterText = getTextFromTestData(mainFileAfterName(testName))
    
    TypeAnnotationSettings.set(getProjectAdapter, oldSettings.asInstanceOf[ScalaCodeStyleSettings])
    assertEquals(mainAfterText, getFileAdapter.getText)

    if (checkSecond) {
      val secondAfterText = getTextFromTestData(secondFileAfterName(testName))
      assertEquals(secondAfterText, secondFile.getText)
    }
  }

  protected def addFileToProject(fileName: String, text: String): PsiFile = {
    inWriteAction {
      val vFile = LightPlatformTestCase.getSourceRoot.createChildData(null, fileName)
      VfsUtil.saveText(vFile, text)
      val psiFile = LightPlatformTestCase.getPsiManager.findFile(vFile)
      assertNotNull("Can't create PsiFile for '" + fileName + "'. Unknown file type most probably.", vFile)
      assertTrue(psiFile.isPhysical)
      vFile.setCharset(CharsetToolkit.UTF8_CHARSET)
      PsiDocumentManager.getInstance(getProjectAdapter).commitAllDocuments()
      psiFile
    }
  }

  protected def getTextFromTestData(fileName: String) = {
    val file = new File(getTestDataPath + fileName)
    FileUtilRt.loadFile(file, CharsetToolkit.UTF8, true)
  }

  protected def getPsiTypeFromText(typeText: String, context: PsiElement): PsiType = {
    val factory: JavaCodeFragmentFactory = JavaCodeFragmentFactory.getInstance(getProjectAdapter)
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

    new ChangeSignatureProcessor(getProjectAdapter, psiMethod, /*generateDelegate = */ false,
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
    val annotationNeeded = TypeAnnotationUtil.isTypeAnnotationNeededMethod(targetMethod, newVisibility)()

    val changeInfo =
      new ScalaChangeInfo(newVisibility, targetMethod.asInstanceOf[ScMethodLike], newName, maybeReturnType.getOrElse(Any), params,
        isAddDefaultValue, Some(annotationNeeded))

    new ScalaChangeSignatureProcessor(getProjectAdapter, changeInfo)
  }
}

