package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase

class Scala3CompletionTest extends ScalaCompletionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  def testDoNotShowAnonymousContextParametersInCompletionList(): Unit = {
    val text =
      s"""def foo(using String, Short): Unit =
         |  val x$$23 = 23
         |  x$$$CARET
         |""".stripMargin
    checkNoBasicCompletion(text, "x$1") //~ `String`
    checkNoBasicCompletion(text, "x$2") //~ `Short`

    doCompletionTest(
      text,
      s"""def foo(using String, Short): Unit =
        |  val x$$23 = 23
        |  x$$23$CARET
        |""".stripMargin,
      "x$23"
    )
  }

  def testSecondCompletionForMethodWithImplicitParams(): Unit = checkLookupItemsExist(
    s"""
       |object Test {
       |  class Blub {
       |    def xxx: Int = 3
       |  }
       |
       |  def blubImplicit(implicit i: Int): Blub = ???
       |
       |  def hehe(i: Int) = 0
       |
       |  hehe(b$CARET)
       |}
       |""".stripMargin,
    invocationCount = 2,
    completionType = CompletionType.SMART
  )("blubImplicit.xxx")

  def testSecondCompletionForMethodWithUsingParams(): Unit = checkLookupItemsExist(
    s"""
       |object Test {
       |  class Blub {
       |    def xxx: Int = 3
       |  }
       |
       |  def blubUsing(using Int): Blub = ???
       |
       |  def hehe(i: Int) = 0
       |
       |  hehe(b$CARET)
       |}
       |""".stripMargin,
    invocationCount = 2,
    completionType = CompletionType.SMART
  )("blubUsing.xxx")

  def testSCL22693(): Unit = checkLookupItemsExist(
    s"""
       |object A {
       |  type MapStrV = [V] =>> Map[String, V]
       |  val map: MapStrV[Int] = Map("ok" -> 1)
       |  map.$CARET
       |}
       |""".stripMargin
  )("values")
}
