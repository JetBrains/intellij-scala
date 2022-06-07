package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.editor.documentationProvider.actions.CreateScalaDocStubAction
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.FindCaretOffset.findCaretOffset
import org.junit.Assert.assertEquals

class CreateScalaDocStubActionTest extends ScalaLightCodeInsightFixtureTestAdapter {

  private def | = EditorTestUtil.CARET_TAG
  private def action = new CreateScalaDocStubAction

  private def doTest(codeBefore: String, codeAfter: String): Unit = {
    val stripTrailingSpaces = true
    configureByText(codeBefore, stripTrailingSpaces)

    val (expected, expectedOffset) = findCaretOffset(codeAfter, stripTrailingSpaces)

    inWriteAction {
      action.actionPerformedImpl(myFixture.getFile, myFixture.getEditor)
    }

    myFixture.checkResult(expected, stripTrailingSpaces)

    assertEquals("Wrong caret offset", expectedOffset, getEditorOffset)
  }

  def testClass_WithoutParameters(): Unit = {
    doTest(
      s"""class ${|}A""",
      s"""/**
         | *
         | */
         |class ${|}A
         |""".stripMargin
    )

    doTest(
      s"""
         |
         |class ${|}A""".stripMargin,
      s"""
         |
         |/**
         | *
         | */
         |class ${|}A
         |""".stripMargin
    )
  }

  def testClass_WithParameters(): Unit =
    doTest(
      s"""class ${|}A(x: Int, str: String)""",
      s"""/**
         | * @param x
         | * @param str
         | */
         |class ${|}A(x: Int, str: String)
         |""".stripMargin
    )

  def testClass_WithParameters_Recreate(): Unit =
    doTest(
      s"""/**
         | * @param x
         | */
         |class ${|}A(x: Int, str: String)""".stripMargin,
      s"""/**
         | * @param x
         | * @param str
         | */
         |class ${|}A(x: Int, str: String)
         |""".stripMargin
    )

  def testClass_Nested(): Unit =
    doTest(
      s"""class Outer {
         |  class ${|}A(x: Int, str: String)
         |}""".stripMargin,
      s"""class Outer {
         |  /**
         |   * @param x
         |   * @param str
         |   */
         |  class ${|}A(x: Int, str: String)
         |}""".stripMargin
    )

  private def configureByText(text: String, stripTrailingSpaces: Boolean): Unit = {
    val (normalizedText, offset) = findCaretOffset(text, stripTrailingSpaces)

    myFixture.configureByText("dummy.scala", normalizedText)
    getEditor.getCaretModel.moveToOffset(offset)
  }
}