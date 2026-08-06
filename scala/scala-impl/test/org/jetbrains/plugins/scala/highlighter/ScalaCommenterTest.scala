package org.jetbrains.plugins.scala.highlighter

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.scala.ScalaLanguage

class ScalaCommenterTest extends BasePlatformTestCase {

  private val Caret = EditorTestUtil.CARET_TAG
  private val SelectionStart = EditorTestUtil.SELECTION_START_TAG
  private val SelectionEnd = EditorTestUtil.SELECTION_END_TAG
  private val ActionCommentLine = IdeActions.ACTION_COMMENT_LINE
  private val ActionCommentBlock = IdeActions.ACTION_COMMENT_BLOCK

  protected def getCommonCodeStyleSettings: CommonCodeStyleSettings =
    CodeStyle.getSettings(getProject).getCommonSettings(ScalaLanguage.INSTANCE)

  private def testAction(before: String, after: String, action: String): Unit = {
    myFixture.configureByText("Foo.scala", before)
    myFixture.performEditorAction(action)
    myFixture.checkResult(after)
  }

  def test_scala_line_comment(): Unit = {
    testAction(
      s"""class Foo {
         |    ${Caret}println()
         |}""".stripMargin,
      s"""class Foo {
         |//    println()
         |}""".stripMargin,
      ActionCommentLine
    )
  }

  def test_scala_line_uncomment(): Unit = {
    testAction(
      s"""class Foo {
         |//    ${Caret}println()
         |}""".stripMargin,
      s"""class Foo {
         |    println()
         |}""".stripMargin,
      ActionCommentLine
    )
  }

  def test_scala_line_comment_LINE_COMMENT_AT_FIRST_COLUMN_DISABLED(): Unit = {
    getCommonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN = false

    val before =
      s"""class Foo {
         |    ${Caret}println()
         |}""".stripMargin
    val after =
      s"""class Foo {
         |    //println()
         |}""".stripMargin
    testAction(before, after, ActionCommentLine)
  }

  def test_scala_line_uncomment_LINE_COMMENT_AT_FIRST_COLUMN_DISABLED(): Unit = {
    getCommonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN = false

    testAction(
      s"""class Foo {
         |    //${Caret}println()
         |}""".stripMargin,
      s"""class Foo {
         |    println()
         |}""".stripMargin,
      ActionCommentLine
    )
  }

  def test_scala_line_comment_LINE_COMMENT_AT_FIRST_COLUMN_DISABLED_LINE_COMMENT_ADD_SPACE_ENABLED(): Unit = {
    getCommonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN = false
    getCommonCodeStyleSettings.LINE_COMMENT_ADD_SPACE = true

    testAction(
      s"""class Foo {
         |    ${Caret}println()
         |}""".stripMargin,
      s"""class Foo {
         |    // println()
         |}""".stripMargin,
      ActionCommentLine
    )
  }

  def test_scala_line_comment_multiline_content_LINE_COMMENT_AT_FIRST_COLUMN_DISABLED_LINE_COMMENT_ADD_SPACE_ENABLED(): Unit = {
    getCommonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN = false
    getCommonCodeStyleSettings.LINE_COMMENT_ADD_SPACE = true

    testAction(
      s"""class Foo {
         |    $Caret${SelectionStart}println()
         |    println()
         |    println()$SelectionEnd
         |}""".stripMargin,
      s"""class Foo {
         |    // println()
         |    // println()
         |    // println()
         |}""".stripMargin,
      ActionCommentLine
    )
  }

  def test_scala_line_uncomment_LINE_COMMENT_AT_FIRST_COLUMN_DISABLED_LINE_COMMENT_ADD_SPACE_ENABLED(): Unit = {
    getCommonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN = false
    getCommonCodeStyleSettings.LINE_COMMENT_ADD_SPACE = true

    testAction(
      s"""class Foo {
         |    // ${Caret}println()
         |}""".stripMargin,
      s"""class Foo {
         |    println()
         |}""".stripMargin,
      ActionCommentLine
    )
  }

  def test_scala_line_uncomment_LINE_COMMENT_AT_FIRST_COLUMN_DISABLED_LINE_COMMENT_ADD_SPACE_ENABLED_1(): Unit = {
    getCommonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN = false
    getCommonCodeStyleSettings.LINE_COMMENT_ADD_SPACE = true

    testAction(
      s"""class Foo {
         |//     ${Caret}println()
         |}""".stripMargin,
      s"""class Foo {
         |    println()
         |}""".stripMargin,
      ActionCommentLine
    )
  }

  def test_scala_line_uncomment_LINE_COMMENT_AT_FIRST_COLUMN_DISABLED_LINE_COMMENT_ADD_SPACE_ENABLED_2(): Unit = {
    getCommonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN = false
    getCommonCodeStyleSettings.LINE_COMMENT_ADD_SPACE = true

    testAction(
      s"""class Foo {
         |     //${Caret}println()
         |}""".stripMargin,
      s"""class Foo {
         |     println()
         |}""".stripMargin,
      ActionCommentLine
    )
  }

  def test_scala_line_uncomment_remove_spaces_on_empty_line(): Unit = {
    getCommonCodeStyleSettings.LINE_COMMENT_AT_FIRST_COLUMN = false
    getCommonCodeStyleSettings.LINE_COMMENT_ADD_SPACE = true

    testAction(
      s"""class Foo {
         |     //$Caret
         |}""".stripMargin,
      s"""class Foo {
         |
         |}""".stripMargin,
      ActionCommentLine
    )
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

  def test_block_comment_for_multiline_expr(): Unit = testAction(
    s"""
       |def foobar = {
       |$SelectionStart  "foo" +
       |    "bar"
       |$SelectionEnd}
       |""".stripMargin,
    """
      |def foobar = {
      |/*
      |  "foo" +
      |    "bar"
      |*/
      |}
      |""".stripMargin,
    ActionCommentBlock
  )

  def test_unblock_comment_for_multiline_expr(): Unit = testAction(
    s"""
       |def foobar = {
       |/*
       |  "foo" +$Caret
       |    "bar"
       |*/
       |}
       |""".stripMargin,
    """
      |def foobar = {
      |  "foo" +
      |    "bar"
      |}
      |""".stripMargin,
    ActionCommentBlock
  )
}
