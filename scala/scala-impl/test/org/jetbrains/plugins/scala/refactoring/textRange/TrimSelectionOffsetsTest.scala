package org.jetbrains.plugins.scala.refactoring.textRange

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.junit.Assert.assertEquals
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.trimSelectionOffsets

class TrimSelectionOffsetsTest extends SimpleTestCase {
  private def createFile(code: String) = fixture.addFileToProject("dummy.scala", code)

  def testEmptyFile(): Unit = {
    val file = createFile("")
    val result = trimSelectionOffsets(file, 0, 0, trimComments = false)
    assertEquals((0, 0), result)
  }

  def testOnlyWhitespaces(): Unit = {
    val file = createFile(
      """
        |
        |
        |""".stripMargin)
    val result = trimSelectionOffsets(file, 0, 0, trimComments = false)
    assertEquals((0, 0), result)
  }

  def testEmptySelection(): Unit = {
    val file = createFile("""
        |object Foo {
        |  def foo():Unit = {}
        |}
        |""".stripMargin.trim)
    val result = trimSelectionOffsets(file, 0, 0, trimComments = false)
    assertEquals((0, 0), result)
  }

  def testFullFileSelection(): Unit = {
    val code =
      """
        |object Foo {
        |  def foo():Unit = {}
        |}
        |""".stripMargin.trim
    val result = trimSelectionOffsets(createFile(code), 0, code.length, trimComments = false)
    assertEquals((0, code.length), result)
  }

  def testWhitespacesAtFrontAndEnd(): Unit = {
    val code =
      """
        |
        |object Foo {
        |  def foo():Unit = {}
        |}
        |
        |""".stripMargin
    val result = trimSelectionOffsets(createFile(code), 0, code.length, trimComments = false)
    assertEquals((2, code.length - 2), result)
  }

  def testWhitespacesAtFrontInPartialSelection(): Unit = {
    val code =
      """
        |object Foo {
        |  def foo():Unit = {}
        |}
        |""".stripMargin.trim
    val startOffset = code.indexOf("def") - 2
    val endOffset = code.indexOf("{}") + 2
    val (start, _) = trimSelectionOffsets(createFile(code), startOffset, endOffset, trimComments = false)
    assertEquals(startOffset + 2, start)
  }

  def testWhitespacesAtEndInPartialSelection(): Unit = {
    val code =
      """
        |object Foo {
        |  def foo():Unit = {}
        |
        |}
        |""".stripMargin.trim
    val startOffset = code.indexOf("def")
    val endOffset = code.indexOf("{}") + 3
    val (_, end) = trimSelectionOffsets(createFile(code), startOffset, endOffset, trimComments = false)
    assertEquals(endOffset - 1, end)
  }

  def testSingleLineCommentAtFront(): Unit = {
    val code =
      """
        |
        |
        |   // comment
        |object Foo {
        |  def foo():Unit = {}
        |}
        |""".stripMargin
    val (start, _) = trimSelectionOffsets(createFile(code), 0, code.length, trimComments = true)
    assertEquals(code.indexOf("object"), start)
  }

  def testSingleLineCommentAtEnd(): Unit = {
    val code =
      """
        |object Foo {
        |  def foo():Unit = {}
        |} // comment
        |""".stripMargin.trim
    val (_, end) = trimSelectionOffsets(createFile(code), 0, code.length, trimComments = true)
    assertEquals(code.indexOf("// comment") - 1, end)
  }

  def testSingleLineCommentInPartialSelection(): Unit = {
    val code =
      """
        |object Foo {
        |  def foo():Unit = {} // comment
        |}
        |""".stripMargin.trim
    val startOffset = code.indexOf("def")
    val endOffset = code.indexOf("comment") + 7
    val result = trimSelectionOffsets(createFile(code), startOffset, endOffset, trimComments = true)
    assertEquals((startOffset, code.indexOf("{}") + 2), result)
  }

  def testManySingleLineComments(): Unit = {
    val code =
      """
        |object Foo {
        |  // comment1
        |  def foo():Unit = {} // comment2
        |}
        |""".stripMargin.trim
    val startOffset = code.indexOf("// comment1")
    val endOffset = code.indexOf("comment2") + 8
    val result = trimSelectionOffsets(createFile(code), startOffset, endOffset, trimComments = true)
    assertEquals((code.indexOf("def"), code.indexOf("{}") + 2), result)
  }

  def testMultiLineCommentAtFront(): Unit = {
    val code =
      """
        |/*
        |  comment line 1
        |  comment line 2
        |*/
        |object Foo {
        |  def foo():Unit = {}
        |}
        |""".stripMargin.trim
    val (start, _) = trimSelectionOffsets(createFile(code), 0, code.length, trimComments = true)
    assertEquals(code.indexOf("object"), start)
  }

  def testMultiLineCommentAtEnd(): Unit = {
    val code =
      """
        |object Foo {
        |  def foo():Unit = {}
        |}
        |/*
        |  comment line 1
        |  comment line 2
        |*/
        |""".stripMargin.trim
    val (_, end) = trimSelectionOffsets(createFile(code), 0, code.length, trimComments = true)
    assertEquals(code.indexOf("/*") - 1, end)
  }

  def testManyMultiLineComments(): Unit = {
    val code =
      """
        |object Foo {
        |/*
        |  comment line 1
        |  comment line 2
        |*/
        |  def foo():Unit = {} /* comment line 3
        |  comment line 4
        |  comment line 5
        |*/
        |}
        |""".stripMargin.trim
    val startOffset = code.indexOf("/*")
    val endOffset = code.lastIndexOf("*/") + 2
    val (start, end) = trimSelectionOffsets(createFile(code), startOffset, endOffset, trimComments = true)
    assertEquals(code.indexOf("def"), start)
    assertEquals(code.indexOf("{}") + 2, end)
  }

  def testStartAndEndInTheMiddleOfAComment(): Unit = {
    val code =
      """
        |object Foo {
        |/*
        |  comment line 1
        |  comment line 2
        |*/
        |  def foo():Unit = {} /* comment line 3
        |  comment line 4
        |  comment line 5
        |*/
        |}
        |""".stripMargin.trim
    val startOffset = code.indexOf("t line 1")
    val endOffset = code.lastIndexOf("t line 5")
    val (start, end) = trimSelectionOffsets(createFile(code), startOffset, endOffset, trimComments = true)
    assertEquals(code.indexOf("def"), start)
    assertEquals(code.indexOf("{}") + 2, end)
  }
}
