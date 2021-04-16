package org.jetbrains.plugins.scala.worksheet

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.SharedTestProjectToken
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.codeInspection.feature.{LanguageFeatureInspection, LanguageFeatureInspectionTestBase}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.worksheet.settings.persistent.WorksheetFilePersistentSettings

abstract class WorksheetLanguageFeatureInspectionBaseTest extends LanguageFeatureInspectionTestBase {

  override protected val fileType: LanguageFileType = WorksheetFileType
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[LanguageFeatureInspection]
  override protected val description = "Advanced language feature: higher-kinded type "
}

class WorksheetLanguageFeatureInspection extends WorksheetLanguageFeatureInspectionBaseTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_12

  def testThatModuleCompilerProfileSettingsAreUsedInWorksheet_HasError(): Unit = {
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(higherKinds = false)
    profile.setSettings(newSettings)
    checkTextHasError(
      s"""def foo[F$START[_]$END, A](fa: F[A]): String = "123"
         |""".stripMargin
    )
  }

  def testThatModuleCompilerProfileSettingsAreUsedInWorksheet_NoError(): Unit = {
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(higherKinds = true)
    profile.setSettings(newSettings)
    checkTextHasNoErrors(
      s"""def foo[F[_], A](fa: F[A]): String = "123"
         |""".stripMargin
    )
  }
}

class WorksheetScratchFileLanguageFeatureInspection extends WorksheetLanguageFeatureInspectionBaseTest {

  override protected val isScratchFile: Boolean = true

  protected val TestCompilerProfile = "TestCompilerProfile"

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(None)

  override protected def onFileCreated(file: PsiFile): Unit =
    WorksheetFilePersistentSettings(file.getVirtualFile).setCompilerProfileName(TestCompilerProfile)

  def testThatSpecifiedCompilerProfileSettingsAreUsedInScratchFile_NoError(): Unit = {
    val profile = createCompilerProfile(TestCompilerProfile)
    val newSettings = profile.getSettings.copy(higherKinds = true)
    profile.setSettings(newSettings)
    checkTextHasNoErrors(
      s"""def foo[F[_], A](fa: F[A]): String = "123"
         |""".stripMargin
    )
  }

  def testThatSpecifiedCompilerProfileSettingsAreUsedInWorksheet_Scratchfile(): Unit = {
    val profile = createCompilerProfile(TestCompilerProfile)
    val newSettings = profile.getSettings.copy(higherKinds = true)
    profile.setSettings(newSettings)
    checkTextHasNoErrors(
      s"""def foo[F[_], A](fa: F[A]): String = "123"
         |""".stripMargin
    )
  }

  //noinspection SameParameterValue
  private def createCompilerProfile(name: String): ScalaCompilerSettingsProfile =
    ScalaCompilerConfiguration.instanceIn(getProject).createCustomProfileForModule(name, getModule)
}