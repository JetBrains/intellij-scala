package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.RevertableChange.withModifiedRegistryValue
import org.jetbrains.plugins.scala.util.assertions.StringAssertions.assertIsBlank
import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.jetbrains.plugins.scala.worksheet.WorksheetUtils
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult.WorksheetRunError
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.TestRunResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.junit.Assert.assertEquals

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
class WorksheetReplIntegration_Scala_2_12_Test
  extends WorksheetReplIntegrationBaseTest
    with WorksheetRuntimeExceptionsTests
    with WorksheetReplIntegration_CommonTests_Since_2_12 {

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12_0))
  @RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
  def testSimpleDeclaration__2_12_0(): Unit =
    testSimpleDeclaration()


  // TODO: why is there this strange error
  //  Error:(10, 14) not found: value unknownVar
  //  val $ires0 = unknownVar
  //  ?
  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_12_12,
    TestScalaVersion.Scala_2_12_6
  ))
  @RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
  def testRestoreErrorPositionsInOriginalFile_ExtraScalaVersions(): Unit =
    testRestoreErrorPositionsInOriginalFile()

  private def TestProfileName = "TestProfileName"

  private val PartialUnificationCompilerOptions = Seq("-Ypartial-unification", "-language:higherKinds")
  private val PartialUnificationTestText =
    """def foo[F[_], A](fa: F[A]): String = "123"
      |foo { x: Int => x * 2 }
      |""".stripMargin

  // -Ypartial-unification is enabled in 2.13 by default, so testing on 2.12
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = PartialUnificationCompilerOptions
    )
    profile.setSettings(newSettings)
    doRenderTest(editor,
      """foo: [F[_], A](fa: F[A])String
        |res0: String = 123""".stripMargin
    )
  }

  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_WithoutSetting(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq.empty
    )
    profile.setSettings(newSettings)
    doResultTest(editor, RunWorksheetActionResult.WorksheetRunError(WorksheetCompilerResult.CompilationError))
  }

  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_NonDefaultProfile(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    worksheetSettings(editor).setCompilerProfileName(TestProfileName)
    val profile = createCompilerProfileForCurrentModule(TestProfileName)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = PartialUnificationCompilerOptions
    )
    profile.setSettings(newSettings)
    doRenderTest(editor,
      """foo: [F[_], A](fa: F[A])String
        |res0: String = 123""".stripMargin
    )
  }

  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_WithoutSetting_NonDefaultProfile(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    worksheetSettings(editor).setCompilerProfileName(TestProfileName)
    val profile = createCompilerProfileForCurrentModule(TestProfileName)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq.empty
    )
    profile.setSettings(newSettings)
    doResultTest(editor, RunWorksheetActionResult.WorksheetRunError(WorksheetCompilerResult.CompilationError))
  }

}
