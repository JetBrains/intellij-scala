package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class Scala3CompletionTest extends ScalaCompletionTestBase {
  @Test
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

  @Test
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

  @Test
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

  @Test
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

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class Scala3InterleavedClausesCompletionTest extends ScalaCompletionTestBase {

  @Test
  def testParameterNameCompletionInInterleavedValueClause(): Unit = checkLookupItemsExist(
    s"""
       |object Test {
       |  def foo[T](firstParam: T)[U](secondParam: U): Unit = ()
       |
       |  foo[Int](1)[String](sec$CARET)
       |}
       |""".stripMargin
  )("secondParam")

  @Test
  def testParameterNameCompletionInThirdInterleavedValueClause(): Unit = checkLookupItemsExist(
    s"""
       |object Test {
       |  def foo[T](firstParam: T)[U](secondParam: U)[V](thirdParam: V): Unit = ()
       |
       |  foo[Int](1)[String]("two")[Boolean](thi$CARET)
       |}
       |""".stripMargin
  )("thirdParam")
}
