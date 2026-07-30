package org.jetbrains.plugins.scala.worksheet

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.highlighting.ScalaCompilerHighlightingTestBase
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class ScratchFileCompilerHighlightingTest extends ScalaCompilerHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
  @Test
  def testSimpleError(): Unit = runWithErrorsFromCompiler(getProject) {
    val expected = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(TextRange.create(16, 19)),
        quickFixDescriptions = Seq.empty,
        msgPrefix = "Found:    (123 : Int)\nRequired: String"
      )
    )

    val scratchFile =
      ScratchRootType.getInstance().createScratchFile(getProject, "simpleError.sc", WorksheetLanguage.INSTANCE, "val x: String = 123")

    try {
      waitUntilHighlightingApplied(scratchFile) {
        openAndFocusEditor(scratchFile)
      }
      doAssertion(scratchFile, expected)
    } finally {
      VfsTestUtil.deleteFile(scratchFile)
    }
  }
}
