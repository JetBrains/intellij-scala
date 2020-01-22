package org.jetbrains.plugins.scala.worksheet

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.codeInspection.feature.{LanguageFeatureInspection, LanguageFeatureInspectionTestBase}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

abstract class WorksheetLanguageFeatureInspectionBaseTest extends LanguageFeatureInspectionTestBase {

  override protected val fileType: LanguageFileType = WorksheetFileType
  override protected val classOfInspection = classOf[LanguageFeatureInspection]
  override protected val description = "Advanced language feature: higher-kinded type "
}

class WorksheetLanguageFeatureInspection extends WorksheetLanguageFeatureInspectionBaseTest {

  def testThatModuleCompilerProfileSettingsAreUsedInWorksheet_HasError(): Unit = {
    getModule.scalaCompilerSettings.higherKinds = false
    checkTextHasError(
      s"""def foo[F$START[_]$END, A](fa: F[A]): String = "123"
         |""".stripMargin
    )
  }

  def testThatModuleCompilerProfileSettingsAreUsedInWorksheet_NoError(): Unit = {
    getModule.scalaCompilerSettings.higherKinds = true
    checkTextHasNoErrors(
      s"""def foo[F[_], A](fa: F[A]): String = "123"
         |""".stripMargin
    )
  }
}

class WorksheetScratchFileLanguageFeatureInspection extends WorksheetLanguageFeatureInspectionBaseTest {

  override protected val isScratchFile: Boolean = true

  protected val TestCompilerProfile = "TestCompilerProfile"

  override protected def onFileCreated(file: PsiFile): Unit =
    WorksheetFileSettings(file).setCompilerProfileName(TestCompilerProfile)

  def testThatSpecifiedCompilerProfileSettingsAreUsedInScratchFile_NoError(): Unit = {
    createCompilerProfile(TestCompilerProfile).getSettings.higherKinds = true
    checkTextHasNoErrors(
      s"""def foo[F[_], A](fa: F[A]): String = "123"
         |""".stripMargin
    )
  }

  def testThatSpecifiedCompilerProfileSettingsAreUsedInWorksheet_Scratchfile(): Unit = {
    createCompilerProfile(TestCompilerProfile).getSettings.higherKinds = true
    checkTextHasNoErrors(
      s"""def foo[F[_], A](fa: F[A]): String = "123"
         |""".stripMargin
    )
  }

  //noinspection SameParameterValue
  private def createCompilerProfile(name: String): ScalaCompilerSettingsProfile =
    ScalaCompilerConfiguration.instanceIn(getProject).createCustomProfileForModule(name, getModule)
}