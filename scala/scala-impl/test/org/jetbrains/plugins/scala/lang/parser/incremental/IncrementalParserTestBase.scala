package org.jetbrains.plugins.scala.lang.parser.incremental

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.base.{EditorActionTestBase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{StringExt, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.jetbrains.plugins.scala.util.extensions.ComparisonFailureOps
import org.junit.ComparisonFailure

abstract class IncrementalParserTestBase extends EditorActionTestBase with AssertionMatchers {
  private implicit def p: Project = getProject

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(this.getClass)


  def doTest(text: String, replaceWith: String = ""): Unit = {
    val (code, Seq(range)) = MarkersUtils.extractMarker(text.withNormalizedSeparator, startMarker = START, endMarker = END)

    val editor = getFixture.getEditor match {
      case null =>
        getFixture.configureByText("test.scala", code)
        getFixture.getEditor
      case editor =>
        // optimization for sequential this.configureByText calls in a single test
        // getFixture.configureByText is quite resource consuming for simple sequence of typing tests
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

    val expectedPsiText = psiToString(ScalaPsiElementFactory.createScalaFileFromText(codeAfter), true)
      .replace("dummy.scala", "test.scala")
    val actualPsiText = psiToString(getFile, true)

    try actualPsiText shouldBe expectedPsiText catch {
      case cf: ComparisonFailure =>
        throw cf.withBeforePrefix(codeAfter)
    }
  }
}
