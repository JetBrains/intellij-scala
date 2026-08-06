package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.junit.Test

trait WorksheetPlainIntegration_Scala_2_12_SpecificTests { self: PlainWorksheetTestBase =>
  private val TestProfileName = "TestProfileName"
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
      """foo: foo[F[_],A](val fa: F[A]) => String
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
      """foo: foo[F[_],A](val fa: F[A]) => String
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
