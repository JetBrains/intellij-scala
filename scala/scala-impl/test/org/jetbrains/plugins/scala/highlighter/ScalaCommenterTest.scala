package org.jetbrains.plugins.scala.highlighter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ScalaCommenterTest extends BasePlatformTestCase {

  def test_scala_cli_comment(): Unit = {
    myFixture.configureByText("foo.scala", "<caret>//> using foo")
    myFixture.performEditorAction("CommentByLineComment")
    myFixture.checkResult("////> using foo")
  }

  def test_scala_cli_uncomment(): Unit = {
    myFixture.configureByText("foo.scala", "<caret>////> using foo")
    myFixture.performEditorAction("CommentByLineComment")
    myFixture.checkResult("//> using foo")
  }

}
