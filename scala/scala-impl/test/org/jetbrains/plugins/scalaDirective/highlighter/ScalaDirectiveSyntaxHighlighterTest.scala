package org.jetbrains.plugins.scalaDirective.highlighter

import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ScalaDirectiveSyntaxHighlighterTest extends BasePlatformTestCase {

  def test_lexer_provided_syntax_highlighting_fallback_attribute_keys(): Unit = {
    val testFile = myFixture.configureByText("foo.scala", "//> using foo bar1 bar2")

    val answer =
      """//>
        |    Scala directive prefix => DEFAULT_DOC_COMMENT
        |using
        |    Scala directive command => DEFAULT_DOC_COMMENT_TAG
        |foo
        |    Scala directive key => DEFAULT_DOC_COMMENT_TAG_VALUE
        |bar1
        |    Scala directive value => DEFAULT_DOC_COMMENT
        |bar2
        |    Scala directive value => DEFAULT_DOC_COMMENT""".stripMargin

    EditorTestUtil.testFileSyntaxHighlighting(testFile, false, answer)
  }
}
