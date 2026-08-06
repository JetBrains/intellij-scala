package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
final class Scala3InlineCompletionTest extends ScalaCompletionTestBase {
  @Test
  def testParamInRegularMethod(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Data(id: Int, column: String)
         |
         |def test1(data: Data): Unit = {
         |  println(data.c$CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""
         |case class Data(id: Int, column: String)
         |
         |def test1(data: Data): Unit = {
         |  println(data.column)
         |}
         |""".stripMargin,
    item = "column"
  )

  @Test
  def testParamInInlineMethod(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Data(id: Int, column: String)
         |
         |inline def test1(data: Data): Unit = {
         |  println(data.c$CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""
         |case class Data(id: Int, column: String)
         |
         |inline def test1(data: Data): Unit = {
         |  println(data.column)
         |}
         |""".stripMargin,
    item = "column"
  )

  @Test
  def testInlineParamInInlineMethod(): Unit = doCompletionTest(
    fileText =
      s"""
         |case class Data(id: Int, column: String)
         |
         |inline def test1(inline data: Data): Unit = {
         |  println(data.c$CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""
         |case class Data(id: Int, column: String)
         |
         |inline def test1(inline data: Data): Unit = {
         |  println(data.column)
         |}
         |""".stripMargin,
    item = "column"
  )
}
