package org.jetbrains.plugins.scala
package refactoring.changeSignature

import java.io.File

import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.{CharsetToolkit, VfsUtil}
import com.intellij.psi._
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.changeSignature.{ChangeSignatureProcessor, ParameterInfoImpl}
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.Assert._
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter

/**
 * Nikolay.Tropin
 * 2014-08-14
 */
abstract class ChangeSignatureFromJavaTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val folderPath: String = baseRootPath() + "changeSignature/fromJava/"
  var targetMethod: PsiMethod = null

  override def getTestDataPath = folderPath

  protected def doTest(newVisibility: String,
                       newName: String,
                       newReturnType: String,
                       newParams: => Array[ParameterInfoImpl]) {
    val testName = getTestName(false)

    val scalaFileName = testName + ".scala"
    val scalaFileText = getTextFromTestData(scalaFileName)
    val scalaFile = addFileToProject(scalaFileName, scalaFileText)

    val javaFileName = testName + ".java"
    configureByFile(javaFileName)

    val targetElement = TargetElementUtilBase.findTargetElement(getEditorAdapter, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED)
    assertTrue("<caret> is not on method name", targetElement.isInstanceOf[PsiMethod])
    targetMethod = targetElement.asInstanceOf[PsiMethod]

    val retType = if (newReturnType != null) getPsiTypeFromText(newReturnType, targetMethod) else targetMethod.getReturnType

    val processor = new ChangeSignatureProcessor(getProjectAdapter, targetMethod, /*generateDelegate = */ false,
      newVisibility, newName, retType, newParams, Array.empty)
    processor.run()

    PostprocessReformattingAspect.getInstance(getProjectAdapter).doPostponedFormatting()

    val afterScalaText = getTextFromTestData(testName + "_after.scala")
    val afterJavaText = getTextFromTestData(testName + "_after.java")

    assertEquals(afterScalaText, scalaFile.getText)
    assertEquals(afterJavaText, getFileAdapter.getText)
  }

  protected def addFileToProject(fileName: String, text: String): PsiFile = {
    val vFile = LightPlatformTestCase.getSourceRoot.createChildData(null, fileName)
    VfsUtil.saveText(vFile, text)
    val psiFile = LightPlatformTestCase.getPsiManager.findFile(vFile)
    assertNotNull("Can't create PsiFile for '" + fileName + "'. Unknown file type most probably.", vFile)
    assertTrue(psiFile.isPhysical)
    vFile.setCharset(CharsetToolkit.UTF8_CHARSET)
    PsiDocumentManager.getInstance(getProjectAdapter).commitAllDocuments()
    psiFile
  }

  protected def getTextFromTestData(fileName: String) = {
    val file = new File(getTestDataPath + fileName)
    FileUtilRt.loadFile(file, CharsetToolkit.UTF8, true)
  }

  protected def getPsiTypeFromText(typeText: String, context: PsiElement): PsiType = {
    val factory: JavaCodeFragmentFactory = JavaCodeFragmentFactory.getInstance(getProjectAdapter)
    factory.createTypeCodeFragment(typeText, context, false).getType
  }

}
