package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

final class ScalaInlineCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val InlinePredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Inline")

  private def doInlineCommandCompletionTest(fileText: String): Unit =
    doCommandCompletionTest(fileText, predicate = InlinePredicate, finishLookup = false)

  private def doInlineCommandCompletionTest(fileText: String, resultText: String): Unit =
    doCommandCompletionTest(fileText, resultText = resultText, predicate = InlinePredicate)

  private def checkNoInlineCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = InlinePredicate)

  @Test
  def methodDeclarationName(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  def myMethod..$CARET(x: Int): Int = x + 1
       |}""".stripMargin
  )

  @Test
  def methodCallReference(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  def myMethod(x: Int): Int = x + 1
       |  val result = myMethod..$CARET(10)
       |}""".stripMargin,
    """object Test {
      |  val result = 10 + 1
      |}""".stripMargin
  )

  @Test
  def valDeclaration(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  val x..$CARET = 42
       |}""".stripMargin
  )

  @Test
  def varDeclaration(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  var counter..$CARET = 0
       |}""".stripMargin
  )

  @Test
  def valReference(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  val x = 1
       |  val y = x..$CARET + 2
       |}""".stripMargin,
    """object Test {
      |  val y = 1 + 2
      |}""".stripMargin
  )

  @Test
  def localValDeclaration(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  def foo(): Int = {
       |    val tmp..$CARET = 42
       |    tmp + 1
       |  }
       |}""".stripMargin,
    """object Test {
      |  def foo(): Int = {
      |    42 + 1
      |  }
      |}""".stripMargin
  )

  @Test
  def typeAliasDeclaration(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  type MyType..$CARET = Int
       |}""".stripMargin
  )

  @Test
  def typeAliasReference(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  type MyType = Int
       |  val x: MyType..$CARET = 1
       |}""".stripMargin
  )

  @Test
  def methodBodyClosingBrace(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  def foo(): Int = {
       |    1 + 1
       |  }..$CARET
       |}""".stripMargin
  )

  @Test
  def methodCallBlockClosingBrace(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  def map(f: Int => Int): Int = f(1)
       |  map { x => x + 1 }..$CARET
       |}""".stripMargin
  )

  @Test
  def methodCallClosingParen(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  def foo(x: Int): Int = x + 1
       |  foo(1)..$CARET
       |}""".stripMargin
  )

  @Test
  def genericCallClosingBracket(): Unit = doInlineCommandCompletionTest(
    s"""object Test {
       |  def myMethod[T](x: T): T = x
       |  myMethod[Int]..$CARET(42)
       |}""".stripMargin
  )

  @Test
  def noInlineForClassName(): Unit = checkNoInlineCommandCompletion(
    s"""class MyClass..$CARET(val x: Int)""".stripMargin
  )

  @Test
  def noInlineForObjectName(): Unit = checkNoInlineCommandCompletion(
    s"""object MyObject..$CARET {
       |  val value = 1
       |}""".stripMargin
  )

  @Test
  def noInlineForTraitName(): Unit = checkNoInlineCommandCompletion(
    s"""trait MyTrait..$CARET {
       |  def method(): Unit
       |}""".stripMargin
  )

  @Test
  def noInlineForMethodParameter(): Unit = checkNoInlineCommandCompletion(
    s"""object Test {
       |  def foo(x..$CARET: Int): Int = x + 1
       |}""".stripMargin
  )

  @Test
  def noInlineForClassConstructorParameter(): Unit = checkNoInlineCommandCompletion(
    s"""class MyClass(x..$CARET: Int)""".stripMargin
  )

  @Test
  def noInlineForClassBodyClosingBrace(): Unit = checkNoInlineCommandCompletion(
    s"""class MyClass {
       |  val x = 1
       |}..$CARET""".stripMargin
  )

  @Test
  def noInlineForObjectBodyClosingBrace(): Unit = checkNoInlineCommandCompletion(
    s"""object MyObject {
       |  val x = 1
       |}..$CARET""".stripMargin
  )

  @Test
  def noInlineForIntegerLiteral(): Unit = checkNoInlineCommandCompletion(
    s"""object Test {
       |  val x = 42.$CARET
       |}""".stripMargin
  )

  @Test
  def noInlineForStringLiteral(): Unit = checkNoInlineCommandCompletion(
    s"""object Test {
       |  val x = "hello".$CARET
       |}""".stripMargin
  )

  @Test
  def noInlineForBooleanLiteral(): Unit = checkNoInlineCommandCompletion(
    s"""object Test {
       |  val x = true.$CARET
       |}""".stripMargin
  )

  @Test
  def noInlineForThisKeyword(): Unit = checkNoInlineCommandCompletion(
    s"""class Test {
       |  def foo(): Test = this.$CARET
       |}""".stripMargin
  )

  @Test
  def noInlineForOpeningBrace(): Unit = checkNoInlineCommandCompletion(
    s"""object Test {.$CARET
       |  val x = 1
       |}""".stripMargin
  )
}
