package org.jetbrains.plugins.scala.lang.completion3.command

import org.junit.Test

final class ScalaCommandCompletionTest extends ScalaCommandCompletionTestBase {
  @Test
  def noCompletionInsideStringBlock(): Unit = checkNoCommandCompletionAtAll(
    s"""
       |class A {
       |  def test(): Unit = {
       |    Test.call(
       |      ""\"some.a.$CARET
       |        *""\".stripMargin('*'), "1"
       |    )
       |  }
       |}
       |
       |object Test {
       |  def call(key: String, key2: String): Unit = {
       |      println(key + key2)
       |  }
       |}
       |""".stripMargin
  )

  @Test
  def noCompletionInsideStringLiteral(): Unit = checkNoCommandCompletionAtAll(
    s"""
       |class A {
       |  def test(): Unit = {
       |    Test.call("some.a.$CARET", "1")
       |  }
       |}
       |
       |object Test {
       |  def call(key: String, key2: String): Unit = {
       |      println(key + key2)
       |  }
       |}
       |""".stripMargin
  )

  @Test
  def completionNotSuppressedInsideStringInterpolation(): Unit = checkHasCommandCompletions(
    s"""
       |class A {
       |  def test(): Unit = {
       |    Test.call(s"some.a.${'$'}{b.$CARET}", "1")
       |  }
       |}
       |
       |object Test {
       |  def call(key: String, key2: String): Unit = {
       |      println(key + key2)
       |  }
       |}
       |""".stripMargin
  )
}
