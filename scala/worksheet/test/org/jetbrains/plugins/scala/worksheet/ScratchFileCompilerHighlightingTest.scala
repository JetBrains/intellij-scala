package org.jetbrains.plugins.scala.worksheet

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.highlighting.ScalaCompilerHighlightingTestBase
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler

class ScratchFileCompilerHighlightingTest extends ScalaCompilerHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

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
      waitUntilFileIsHighlighted(scratchFile)
      doAssertion(scratchFile, expected)
    } finally {
      VfsTestUtil.deleteFile(scratchFile)
    }
  }
}
