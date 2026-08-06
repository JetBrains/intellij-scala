package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

final class ScalaShowUsagesCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val ShowUsagesPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Show usages")

  private def doShowUsagesCommandCompletionTest(fileText: String): Unit =
    doCommandCompletionTest(fileText, predicate = ShowUsagesPredicate, finishLookup = false)

  private def checkNoShowUsagesCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = ShowUsagesPredicate)

  @Test
  def functionParameter(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(${start}param$end..$CARET: Int): Unit = param + 2
         |}""".stripMargin
  )

  @Test
  def functionParameterUsage(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(param: Int): Unit = ${start}param$end..$CARET + 2
         |}""".stripMargin
  )

  @Test
  def objectLevelVal(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val ${start}count$end..$CARET = 42
         |}""".stripMargin
  )

  @Test
  def classLevelVar(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""class Test {
         |  var ${start}counter$end..$CARET = 0
         |}""".stripMargin
  )

  @Test
  def methodDeclaration(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}myMethod$end..$CARET(x: Int): Int = x + 1
         |}""".stripMargin
  )

  @Test
  def methodCallReference(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def myMethod(x: Int): Int = x + 1
         |  val result = ${start}myMethod$end..$CARET(10)
         |}""".stripMargin
  )

  @Test
  def valUsageInExpression(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val x = 1
         |  val y = ${start}x$end..$CARET + 1
         |}""".stripMargin
  )

  @Test
  def className(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""class ${start}MyClass$end..$CARET(val x: Int)""".stripMargin
  )

  @Test
  def classReference(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""class MyClass(val x: Int)
         |
         |object Test {
         |  val obj: ${start}MyClass$end..$CARET = new MyClass(1)
         |}""".stripMargin
  )

  @Test
  def objectName(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""object ${start}MyObject$end..$CARET {
         |  val value = 1
         |}""".stripMargin
  )

  @Test
  def traitName(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""trait ${start}MyTrait$end..$CARET {
         |  def method(): Unit
         |}""".stripMargin
  )

  @Test
  def traitReference(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""trait MyTrait
         |
         |class Child extends ${start}MyTrait$end..$CARET""".stripMargin
  )

  @Test
  def typeAlias(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""object Test {
         |  type ${start}MyType$end..$CARET = Int
         |}""".stripMargin
  )

  @Test
  def typeAliasReference(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""object Test {
         |  type MyType = Int
         |  val x: ${start}MyType$end..$CARET = 1
         |}""".stripMargin
  )

  @Test
  def caseClassName(): Unit = doShowUsagesCommandCompletionTest(
    fileText =
      s"""case class ${start}Point$end..$CARET(x: Int, y: Int)""".stripMargin
  )

  @Test
  def noShowUsagesForIntegerLiteral(): Unit = checkNoShowUsagesCommandCompletion(
    s"""object Test {
       |  val x = 42.$CARET
       |}""".stripMargin
  )

  @Test
  def noShowUsagesForThisKeyword(): Unit = checkNoShowUsagesCommandCompletion(
    s"""class Test {
       |  def foo(): Test = this.$CARET
       |}""".stripMargin
  )

  @Test
  def noShowUsagesForClosingBrace(): Unit = checkNoShowUsagesCommandCompletion(
    s"""object Test {
       |  def foo() = {}.$CARET
       |}""".stripMargin
  )

  @Test
  def noShowUsagesForOpeningBrace(): Unit = checkNoShowUsagesCommandCompletion(
    s"""object Test {.$CARET
       |  val x = 1
       |}""".stripMargin
  )

  @Test
  def noShowUsagesForClosingParen(): Unit = checkNoShowUsagesCommandCompletion(
    s"""object Test {
       |  def foo(x: Int) = x
       |  foo(1).$CARET
       |}""".stripMargin
  )

  @Test
  def noShowUsagesForComma(): Unit = checkNoShowUsagesCommandCompletion(
    s"""object Test {
       |  def foo(x: Int, y: Int) = x + y
       |  foo(1,.$CARET 2)
       |}""".stripMargin
  )

  @Test
  def noShowUsagesForClosingBracket(): Unit = checkNoShowUsagesCommandCompletion(
    s"""class Container[T]
       |
       |object Test {
       |  new Container[Int].$CARET
       |}""".stripMargin
  )

  @Test
  def noShowUsagesForBooleanLiteral(): Unit = checkNoShowUsagesCommandCompletion(
    s"""object Test {
       |  val x = true.$CARET
       |}""".stripMargin
  )

  @Test
  def noShowUsagesForNullLiteral(): Unit = checkNoShowUsagesCommandCompletion(
    s"""object Test {
       |  val x = null.$CARET
       |}""".stripMargin
  )
}
