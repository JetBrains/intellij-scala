package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.util.runners.{RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
class WorksheetReplIntegration_Scala_2_12_Test
  extends WorksheetReplIntegrationBaseTest
    with WorksheetRuntimeExceptionsTests
    with WorksheetReplIntegration_CommonTests_Since_2_12 {

  private def TestProfileName = "TestProfileName"

  private val PartialUnificationCompilerOptions = Seq("-Ypartial-unification", "-language:higherKinds")
  private val PartialUnificationTestText =
    """def foo[F[_], A](fa: F[A]): String = "123"
      |foo { x: Int => x * 2 }
      |""".stripMargin

  // -Ypartial-unification is enabled in 2.13 by default, so testing on 2.12
  @Test
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile(): Unit = {
    val editorAndFile = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    setAdditionalCompilerOptions(editorAndFile.psiFile, PartialUnificationCompilerOptions)
    doRenderTest(editorAndFile,
      """foo: [F[_], A](fa: F[A])String
        |res0: String = 123""".stripMargin
    )
  }

  @Test
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_WithoutSetting(): Unit = {
    val editorAndFile = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    setAdditionalCompilerOptions(editorAndFile.psiFile, Seq.empty)
    doResultTest(editorAndFile, RunWorksheetActionResult.WorksheetRunError(WorksheetCompilerResult.CompilationError))
  }

  @Test
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_NonDefaultProfile(): Unit = {
    val editorAndFile = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    worksheetSettings(editorAndFile.editor).setCompilerProfileName(TestProfileName)
    val profile = createCompilerProfileForCurrentModule(TestProfileName)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = PartialUnificationCompilerOptions
    )
    profile.setSettings(newSettings)
    doRenderTest(editorAndFile,
      """foo: [F[_], A](fa: F[A])String
        |res0: String = 123""".stripMargin
    )
  }

  @Test
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_WithoutSetting_NonDefaultProfile(): Unit = {
    val editorAndFile = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    worksheetSettings(editorAndFile.editor).setCompilerProfileName(TestProfileName)
    val profile = createCompilerProfileForCurrentModule(TestProfileName)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq.empty
    )
    profile.setSettings(newSettings)
    doResultTest(editorAndFile, RunWorksheetActionResult.WorksheetRunError(WorksheetCompilerResult.CompilationError))
  }

}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12_0))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
class WorksheetReplIntegration_Scala_2_12_0_HealthCheckTest
  extends WorksheetReplIntegrationBaseTest
    with WorksheetReplIntegrationHealthCheckTest_Since_2_11

// TODO: why is there this strange error
//  Error:(10, 14) not found: value unknownVar
//  val $ires0 = unknownVar
//  ?
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12_6,
  TestScalaVersion.Scala_2_12_12
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_11))
class WorksheetReplIntegration_Scala_2_12_RestoreErrorPositionsExtraTests
  extends WorksheetReplIntegrationBaseTest
    with WorksheetReplIntegrationRestoreErrorPositionsInOriginalFileTest_Since_2_12
