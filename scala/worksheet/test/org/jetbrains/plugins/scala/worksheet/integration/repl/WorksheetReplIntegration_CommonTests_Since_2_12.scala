package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.util.RevertableChange.withModifiedRegistryValue
import org.jetbrains.plugins.scala.util.assertions.StringAssertions.assertIsBlank
import org.jetbrains.plugins.scala.worksheet.WorksheetUtils
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult.WorksheetRunError
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.TestRunResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.junit.Assert.assertEquals

trait WorksheetReplIntegration_CommonTests_Since_2_12
  extends WorksheetReplIntegration_CommonTests_Since_2_11 {
  self: WorksheetReplIntegrationBaseTest with WorksheetRuntimeExceptionsTests =>

  override def testCompilationErrorsAndWarnings_ComplexTest(): Unit =
    baseTestCompilationErrorsAndWarnings_ComplexTest(
      """Warning:(2, 7) match may not be exhaustive.
        |It would fail on the following inputs: None, Some((x: Int forSome x not in 42))
        |Option(42) match {
        |
        |Error:(11, 13) not found: value Sum
        |def foo = Sum(Product(Number(2),
        |
        |Error:(11, 17) not found: value Product
        |def foo = Sum(Product(Number(2),
        |
        |Error:(11, 25) class java.lang.Number is not a value
        |def foo = Sum(Product(Number(2),
        |
        |Error:(12, 5) class java.lang.Number is not a value
        |Number(3)))
        |""".stripMargin.trim
    )

  override def testRestoreErrorPositionsInOriginalFile(): Unit =
    withModifiedRegistryValue(WorksheetUtils.ContinueOnFirstFailure, newValue = true).run {
      val expectedCompilerOutput =
        """Error:(2, 7) not found: value unknown1
          |unknown1
          |Error:(8, 1) not found: value unknownVar
          |unknownVar = 23 +
          |Error:(11, 14) not found: value unknownVar
          |val $ires0 = unknownVar
          |Error:(12, 5) not found: value unknown3
          |unknown3 +
          |Error:(13, 7) not found: value unknown4
          |unknown4
          |Error:(17, 9) not found: value unknown5
          |unknown5
          |Error:(22, 5) not found: value unknown6
          |unknown6; val z =
          |Error:(23, 7) not found: value unknown7
          |unknown7
          |Error:(29, 5) not found: value unknown8
          |unknown8
          |Error:(35, 5) not found: value unknown9
          |unknown9
          |Error:(39, 5) not found: value unknown10
          |unknown10
          |Error:(48, 5) not found: value unknown11
          |unknown11
          |Error:(50, 7) not found: value unknown12
          |unknown12
          |Error:(59, 7) not found: value unknown13
          |unknown13
          |Error:(62, 3) not found: value unknown14
          |unknown14""".stripMargin

      val TestRunResult(editorAndFile, evaluationResult) =
        doRenderTestWithoutCompilationChecks2(RestoreErrorPositionsInOriginalFileCode, output => assertIsBlank(output))
      assertEquals(WorksheetRunError(WorksheetCompilerResult.CompilationError), evaluationResult)
      assertCompilerMessages(editorAndFile.editor)(expectedCompilerOutput)
    }
}
