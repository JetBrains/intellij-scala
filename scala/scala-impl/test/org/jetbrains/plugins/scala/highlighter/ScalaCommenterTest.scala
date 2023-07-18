package org.jetbrains.plugins.scala.highlighter

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ScalaCommenterTest extends BasePlatformTestCase {

  private val Caret = EditorTestUtil.CARET_TAG
  private val SelectionStart = EditorTestUtil.SELECTION_START_TAG
  private val SelectionEnd = EditorTestUtil.SELECTION_END_TAG
  private val ActionCommentLine = IdeActions.ACTION_COMMENT_LINE
  private val ActionCommentBlock = IdeActions.ACTION_COMMENT_BLOCK

  private def testAction(before: String, after: String, action: String): Unit = {
    myFixture.configureByText("Foo.scala", before)
    myFixture.performEditorAction(action)
    myFixture.checkResult(after)
  }

  def test_scala_line_comment(): Unit = {
    val before = s"${Caret}class Foo {}"
    val after = "//class Foo {}"
    testAction(before, after, ActionCommentLine)
  }

  def test_scala_line_uncomment(): Unit = {
    val before = s"$Caret//class Foo {}"
    val after = "class Foo {}"
    testAction(before, after, ActionCommentLine)
  }

  def test_scala_single_line_block_comment(): Unit = {
    val before = s"${SelectionStart}class Foo {}$SelectionEnd"
    val after = "/*class Foo {}*/"
    testAction(before, after, ActionCommentBlock)
  }

  def test_scala_single_line_block_uncomment(): Unit = {
    val before = s"$SelectionStart/*class Foo {}*/$SelectionEnd"
    val after = "class Foo {}"
    testAction(before, after, ActionCommentBlock)
  }

  def test_scala_multi_line_block_comment(): Unit = {
    val before =
      s"""${SelectionStart}class Foo {
         |  def bar(): Unit = ()
         |}$SelectionEnd""".stripMargin

    val after =
      """/*class Foo {
        |  def bar(): Unit = ()
        |}*/""".stripMargin

    testAction(before, after, ActionCommentBlock)
  }

  def test_scala_multi_line_block_uncomment(): Unit = {
    val before =
      s"""$SelectionStart/*class Foo {
         |  def bar(): Unit = ()
         |}*/$SelectionEnd""".stripMargin

    val after =
      """class Foo {
        |  def bar(): Unit = ()
        |}""".stripMargin

    testAction(before, after, ActionCommentBlock)
  }

  def test_scala_directive_line_comment(): Unit = {
    val before = s"$Caret//> using foo"
    val after = "////> using foo"
    testAction(before, after, ActionCommentLine)
  }

  def test_scala_directive_line_uncomment(): Unit = {
    val before = s"$Caret////> using foo"
    val after = "//> using foo"
    testAction(before, after, ActionCommentLine)
  }

  def test_scala_directive_block_comment(): Unit = {
    val before = s"$SelectionStart//> using foo$SelectionEnd"
    val after = "/*//> using foo*/"
    testAction(before, after, ActionCommentBlock)
  }

  def test_scala_directive_block_uncomment(): Unit = {
    val before = s"$SelectionStart/*//> using foo*/$SelectionEnd"
    val after = "//> using foo"
    testAction(before, after, ActionCommentBlock)
  }
}
