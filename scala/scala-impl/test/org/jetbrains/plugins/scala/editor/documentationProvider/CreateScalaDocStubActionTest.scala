package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.editor.documentationProvider.actions.CreateScalaDocStubAction
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.util.FindCaretOffset.findCaretOffset
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.assertEquals

abstract class CreateScalaDocStubActionTestBase extends ScalaLightCodeInsightFixtureTestCase {
  protected val | = EditorTestUtil.CARET_TAG

  private def action = new CreateScalaDocStubAction

  protected def doTest(codeBefore: String, codeAfter: String): Unit = {
    val stripTrailingSpaces = true
    configureByText(codeBefore, stripTrailingSpaces)

    val (expected, expectedOffset) = findCaretOffset(codeAfter, stripTrailingSpaces)

    inWriteAction {
      action.actionPerformedImpl(myFixture.getFile, myFixture.getEditor)
    }

    myFixture.checkResult(expected, stripTrailingSpaces)

    assertEquals("Wrong caret offset", expectedOffset, getEditor.getCaretModel.getOffset)
  }

  private def configureByText(text: String, stripTrailingSpaces: Boolean): Unit = {
    val (normalizedText, offset) = findCaretOffset(text, stripTrailingSpaces)

    myFixture.configureByText("dummy.scala", normalizedText)
    getEditor.getCaretModel.moveToOffset(offset)
  }
}

class CreateScalaDocStubActionTest extends CreateScalaDocStubActionTestBase {

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
}

final class CreateScalaDocStubActionTest_Scala3 extends CreateScalaDocStubActionTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testIndentedClass_WithoutParameters(): Unit = {
    doTest(
      s"""object Test:
         |  class ${|}A
         |""".stripMargin,
      s"""object Test:
         |  /**
         |   *
         |   */
         |  class ${|}A
         |""".stripMargin
    )

    doTest(
      s"""object Test:
         |
         |
         |  class ${|}A
         |""".stripMargin,
      s"""object Test:
         |
         |
         |  /**
         |   *
         |   */
         |  class ${|}A
         |""".stripMargin
    )
  }

  def testIndentedClass_WithParameters(): Unit =
    doTest(
      s"""object Test:
         |  class ${|}A(x: Int, str: String)
         |""".stripMargin,
      s"""object Test:
         |  /**
         |   * @param x
         |   * @param str
         |   */
         |  class ${|}A(x: Int, str: String)
         |""".stripMargin
    )

  def testIndentedClass_WithParameters_Recreate(): Unit =
    doTest(
      s"""object Test:
         |  /**
         |   * @param x
         |   */
         |  class ${|}A(x: Int, str: String)
         |""".stripMargin,
      s"""object Test:
         |  /**
         |   * @param x
         |   * @param str
         |   */
         |  class ${|}A(x: Int, str: String)
         |""".stripMargin
    )

  def testFunction_Nested(): Unit =
    doTest(
      s"""object Test:
         |
         |  def foo(): Unit =
         |
         |    def b${|}ar(i: Int): Unit =
         |      println(i)
         |
         |    println(0)
         |    bar(1)
         |    bar(2)
         |  end foo
         |""".stripMargin,
      s"""object Test:
         |
         |  def foo(): Unit =
         |
         |    /**
         |     * @param i
         |     */
         |    def b${|}ar(i: Int): Unit =
         |      println(i)
         |
         |    println(0)
         |    bar(1)
         |    bar(2)
         |  end foo
         |""".stripMargin
    )
}
