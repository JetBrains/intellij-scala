package org.jetbrains.plugins.scalaCli.highlighter

import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.scala.util.TestUtils

class ScalaCliSyntaxHighlighterTest extends BasePlatformTestCase {

  private val answerFilePath: String = TestUtils.getTestDataPath + "/ScalaCliSyntaxHighlighter/fallback_attribute_keys.txt"

  def test_lexer_provided_syntax_highlighting_fallback_attribute_keys(): Unit = {
    val testFile = myFixture.configureByText("foo.scala", "//> using foo bar1 bar2")
    EditorTestUtil.testFileSyntaxHighlighting(testFile, answerFilePath, false)
  }
}
