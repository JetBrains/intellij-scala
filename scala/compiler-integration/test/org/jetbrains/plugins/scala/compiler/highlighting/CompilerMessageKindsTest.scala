package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import org.jetbrains.jps.incremental.scala.MessageKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.{DynamicTest, TestFactory}

class CompilerMessageKindsTest {

  @TestFactory
  def highlightInfoTypeTests(): Array[DynamicTest] = {
    case class TestCase(displayName: String, text: String, kind: MessageKind, fatalWarningsFlag: Boolean, unusedImportsFlag: Boolean, expected: HighlightInfoType)

    def assertHighlightInfoType(testCase: TestCase): Unit = {
      val TestCase(_, text, kind, fatalWarningsFlag, unusedImportsFlag, expected) = testCase
      val actual = CompilerMessageKinds.highlightInfoType(kind, text, fatalWarningsFlag, unusedImportsFlag)
      assertEquals(expected, actual)
    }

    Array(
      TestCase(
        displayName = "wrongRef1",
        text = "Value myValue is not a member of MyType",
        kind = MessageKind.Error,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.WRONG_REF),
      TestCase(
        displayName = "wrongRef2",
        text = "Not found: myValue",
        kind = MessageKind.Error,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.WRONG_REF),
      TestCase(
        displayName = "wrongRef3",
        text = "Cannot find symbol myValue",
        kind = MessageKind.Error,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.WRONG_REF),
      TestCase(
        displayName = "errorUnusedImportFatalWarningsWithFlag",
        text = "Unused import",
        kind = MessageKind.Error,
        fatalWarningsFlag = true,
        unusedImportsFlag = true,
        expected = HighlightInfoType.ERROR),
      TestCase(
        displayName = "errorUnusedImportNoFlag",
        text = "Unused import",
        kind = MessageKind.Error,
        fatalWarningsFlag = true,
        unusedImportsFlag = false,
        expected = HighlightInfoType.UNUSED_SYMBOL),
      TestCase(
        displayName = "errorUnusedImportNoFatalWarningsNoFlag",
        text = "Unused import",
        kind = MessageKind.Error,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.UNUSED_SYMBOL),
      TestCase(
        displayName = "regularError",
        text = "Some random compiler error",
        kind = MessageKind.Error,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.ERROR),
      TestCase(
        displayName = "upgradeWarningUnusedImportFatalWarningsWithFlag",
        text = "Unused import",
        kind = MessageKind.Warning,
        fatalWarningsFlag = true,
        unusedImportsFlag = true,
        expected = HighlightInfoType.ERROR),
      TestCase(
        displayName = "warningUnusedImportNoFatalWarningsWithFlag",
        text = "Unused import",
        kind = MessageKind.Warning,
        fatalWarningsFlag = false,
        unusedImportsFlag = true,
        expected = HighlightInfoType.WARNING),
      TestCase(
        displayName = "warningUnusedImportFatalWarningsNoFlag",
        text = "Unused import",
        kind = MessageKind.Warning,
        fatalWarningsFlag = true,
        unusedImportsFlag = false,
        expected = HighlightInfoType.UNUSED_SYMBOL),
      TestCase(
        displayName = "warningUnusedImportNoFatalWarningsNoFlag",
        text = "Unused import",
        kind = MessageKind.Warning,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.UNUSED_SYMBOL),
      TestCase(
        displayName = "regularWarning",
        text = "Some random compiler warning",
        kind = MessageKind.Warning,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.WARNING),
      TestCase(
        displayName = "upgradeRegularWarningToErrorFatalWarnings",
        text = "Some random compiler warning",
        kind = MessageKind.Warning,
        fatalWarningsFlag = true,
        unusedImportsFlag = false,
        expected = HighlightInfoType.ERROR),
      TestCase(
        displayName = "regularInfo",
        text = "Some random compiler info",
        kind = MessageKind.Info,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.WEAK_WARNING),
      TestCase(
        displayName = "internalBuilderError",
        text = "Some random compiler internal builder error",
        kind = MessageKind.InternalBuilderError,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.INFORMATION),
      TestCase(
        displayName = "jpsInfo",
        text = "Some random jps info",
        kind = MessageKind.JpsInfo,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.INFORMATION),
      TestCase(
        displayName = "progress",
        text = "Some random progress message",
        kind = MessageKind.Progress,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.INFORMATION),
      TestCase(
        displayName = "other",
        text = "Some random other message",
        kind = MessageKind.Other,
        fatalWarningsFlag = false,
        unusedImportsFlag = false,
        expected = HighlightInfoType.INFORMATION)
    ).map(testCase => dynamicTest(testCase.displayName, () => assertHighlightInfoType(testCase)))
  }
}
