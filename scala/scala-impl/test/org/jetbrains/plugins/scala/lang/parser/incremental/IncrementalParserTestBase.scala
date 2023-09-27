package org.jetbrains.plugins.scala.lang.parser.incremental

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.jetbrains.plugins.scala.util.extensions.ComparisonFailureOps
import org.junit.ComparisonFailure

abstract class IncrementalParserTestBase extends EditorActionTestBase with AssertionMatchers {
  private implicit def p: Project = getProject

  def doTest(text: String, replaceWith: String = ""): Unit = {
    val (code, Seq(range)) = MarkersUtils.extractMarker(text, startMarker = START, endMarker = END)

    val editor = myFixture.getEditor match {
      case null =>
        myFixture.configureByText("test.scala", code)
        myFixture.getEditor
      case editor =>
        // optimization for sequential this.configureByText calls in a single test
        // myFixture.configureByText is quite resource consuming for simple sequence of typing tests
        inWriteCommandAction {
          editor.getDocument.setText(code)
          editor.getDocument.commit(getProject)
        }
        editor
    }

    // do replace and reparse
    inWriteCommandAction {
      val doc = editor.getDocument
      doc.replaceString(range.getStartOffset, range.getEndOffset, replaceWith)
      doc.commit(getProject)
    }

    val codeAfter = code.patch(range.getStartOffset, replaceWith, range.getLength)

    val expectedPsiText =
      psiToString(
        ScalaPsiElementFactory.createScalaFileFromText(
          codeAfter,
          ScalaFeatures.onlyByVersion(version),
          shouldTrimText = false
        ),
        true
      ).replace("dummy.scala", "test.scala")

    val actualPsiText = psiToString(getFile, true)

    try actualPsiText shouldBe expectedPsiText catch {
      case cf: ComparisonFailure =>
        throw cf.withBeforePrefix(codeAfter)
    }
  }
}
