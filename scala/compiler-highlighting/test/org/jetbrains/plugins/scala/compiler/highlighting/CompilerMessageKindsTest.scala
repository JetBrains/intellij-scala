package org.jetbrains.plugins.scala.compiler.highlighting

import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.jps.incremental.scala.MessageKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.unused

@RunWith(classOf[JUnitParamsRunner])
class CompilerMessageKindsTest {

  private case class TestCaseParams(
    displayName: String,
    text: String,
    kind: MessageKind,
    fatalWarningsFlag: Boolean,
    unusedImportsFlag: Boolean,
    expected: HighlightInfoType
  ) {
    override def toString: String = displayName
  }

  @unused("used reflectively by the @Parameters annotation")
  private def testParameters: Array[TestCaseParams] = Array(
    TestCaseParams(
      displayName = "wrongRef1",
      text = "Value myValue is not a member of MyType",
      kind = MessageKind.Error,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.WrongRef),
    TestCaseParams(
      displayName = "wrongRef2",
      text = "Not found: myValue",
      kind = MessageKind.Error,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.WrongRef),
    TestCaseParams(
      displayName = "wrongRef3",
      text = "Cannot find symbol myValue",
      kind = MessageKind.Error,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.WrongRef),
    TestCaseParams(
      displayName = "errorUnusedImportFatalWarningsWithFlag",
      text = "Unused import",
      kind = MessageKind.Error,
      fatalWarningsFlag = true,
      unusedImportsFlag = true,
      expected = HighlightInfoType.Error),
    TestCaseParams(
      displayName = "errorUnusedImportNoFlag",
      text = "Unused import",
      kind = MessageKind.Error,
      fatalWarningsFlag = true,
      unusedImportsFlag = false,
      expected = HighlightInfoType.UnusedSymbol),
    TestCaseParams(
      displayName = "errorUnusedImportNoFatalWarningsNoFlag",
      text = "Unused import",
      kind = MessageKind.Error,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.UnusedSymbol),
    TestCaseParams(
      displayName = "regularError",
      text = "Some random compiler error",
      kind = MessageKind.Error,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.Error),
    TestCaseParams(
      displayName = "upgradeWarningUnusedImportFatalWarningsWithFlag",
      text = "Unused import",
      kind = MessageKind.Warning,
      fatalWarningsFlag = true,
      unusedImportsFlag = true,
      expected = HighlightInfoType.Error),
    TestCaseParams(
      displayName = "warningUnusedImportNoFatalWarningsWithFlag",
      text = "Unused import",
      kind = MessageKind.Warning,
      fatalWarningsFlag = false,
      unusedImportsFlag = true,
      expected = HighlightInfoType.Warning),
    TestCaseParams(
      displayName = "warningUnusedImportFatalWarningsNoFlag",
      text = "Unused import",
      kind = MessageKind.Warning,
      fatalWarningsFlag = true,
      unusedImportsFlag = false,
      expected = HighlightInfoType.UnusedSymbol),
    TestCaseParams(
      displayName = "warningUnusedImportNoFatalWarningsNoFlag",
      text = "Unused import",
      kind = MessageKind.Warning,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.UnusedSymbol),
    TestCaseParams(
      displayName = "regularWarning",
      text = "Some random compiler warning",
      kind = MessageKind.Warning,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.Warning),
    TestCaseParams(
      displayName = "upgradeRegularWarningToErrorFatalWarnings",
      text = "Some random compiler warning",
      kind = MessageKind.Warning,
      fatalWarningsFlag = true,
      unusedImportsFlag = false,
      expected = HighlightInfoType.Error),
    TestCaseParams(
      displayName = "regularInfo",
      text = "Some random compiler info",
      kind = MessageKind.Info,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.WeakWarning),
    TestCaseParams(
      displayName = "internalBuilderError",
      text = "Some random compiler internal builder error",
      kind = MessageKind.InternalBuilderError,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.Information),
    TestCaseParams(
      displayName = "jpsInfo",
      text = "Some random jps info",
      kind = MessageKind.JpsInfo,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.Information),
    TestCaseParams(
      displayName = "progress",
      text = "Some random progress message",
      kind = MessageKind.Progress,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.Information),
    TestCaseParams(
      displayName = "other",
      text = "Some random other message",
      kind = MessageKind.Other,
      fatalWarningsFlag = false,
      unusedImportsFlag = false,
      expected = HighlightInfoType.Information)
  )

  @Test
  @Parameters(method = "testParameters")
  @TestCaseName("{method}[{0}]")
  def highlightInfoTypeTest(params: TestCaseParams): Unit = {
    val TestCaseParams(_, text, kind, fatalWarningsFlag, unusedImportsFlag, expected) = params
    val actual = CompilerMessageKinds.highlightInfoType(kind, text, fatalWarningsFlag, unusedImportsFlag)
    assertEquals(expected, actual)
  }
}
