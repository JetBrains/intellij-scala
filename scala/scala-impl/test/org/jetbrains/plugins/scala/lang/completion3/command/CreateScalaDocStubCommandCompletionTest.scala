package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.lookup.LookupElement
import junit.framework.TestCase.{assertEquals, assertTrue, fail}
import org.junit.Test

final class CreateScalaDocStubCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val AddScalaDocCommandCompletionPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Add ScalaDoc")

  private def doAddScalaDocCommandCompletionTest(fileText: String, resultText: String): Unit =
    doCommandCompletionTest(
      fileText,
      resultText = resultText,
      predicate = AddScalaDocCommandCompletionPredicate,
      checkPreview = {
        case diff: IntentionPreviewInfo.CustomDiff =>
          assertEquals(resultText, diff.modifiedText())
          assertTrue(diff.showLineNumbers())
        case preview => fail(s"Custom diff preview expected, got ${preview.getClass}")
      })

  private def checkNoAddScalaDocCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, AddScalaDocCommandCompletionPredicate)

  @Test
  def className(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""class ${start}A$end..$CARET""".stripMargin,
    resultText =
      s"""/**
         | *
         | */
         |class A""".stripMargin
  )

  @Test
  def traitName(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""trait ${start}MyTrait$end..$CARET""".stripMargin,
    resultText =
      s"""/**
         | *
         | */
         |trait MyTrait""".stripMargin
  )

  @Test
  def objectName(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""object ${start}MyObject$end..$CARET""".stripMargin,
    resultText =
      s"""/**
         | *
         | */
         |object MyObject""".stripMargin
  )

  @Test
  def methodWithEmptyParens(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}foo$end..$CARET(): Unit = ()
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  /**
         |   *
         |   */
         |  def foo(): Unit = ()
         |}""".stripMargin
  )

  @Test
  def methodWithoutParens(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}foo$end..$CARET: Int = 1
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  /**
         |   * @return
         |   */
         |  def foo: Int = 1
         |}""".stripMargin
  )

  @Test
  def typeAlias(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""object Test {
         |  type ${start}MyType$end..$CARET = Int
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  /**
         |   *
         |   */
         |  type MyType = Int
         |}""".stripMargin
  )

  @Test
  def nestedClass(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""class Outer {
         |  class ${start}Inner$end..$CARET
         |}""".stripMargin,
    resultText =
      s"""class Outer {
         |  /**
         |   *
         |   */
         |  class Inner
         |}""".stripMargin
  )

  @Test
  def noCompletionForClassWithExistingDoc(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""/**
       | * existing
       | */
       |class A..$CARET""".stripMargin
  )

  @Test
  def noCompletionForMethodWithExistingDoc(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""object Test {
       |  /**
       |   * existing
       |   */
       |  def foo..$CARET(): Unit = ()
       |}""".stripMargin
  )

  @Test
  def noCompletionForValDeclaration(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""object Test {
       |  val x..$CARET = 42
       |}""".stripMargin
  )

  @Test
  def noCompletionForVarDeclaration(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""object Test {
       |  var counter..$CARET = 0
       |}""".stripMargin
  )

  @Test
  def noCompletionForMethodParameter(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""object Test {
       |  def foo(x..$CARET: Int): Int = x
       |}""".stripMargin
  )

  @Test
  def noCompletionForIntegerLiteral(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""object Test {
       |  val x = 42.$CARET
       |}""".stripMargin
  )

  @Test
  def noCompletionForStringLiteral(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""object Test {
       |  val x = "hello".$CARET
       |}""".stripMargin
  )

  @Test
  def noCompletionForThisKeyword(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""class Test {
       |  def foo(): Test = this.$CARET
       |}""".stripMargin
  )

  @Test
  def noCompletionForClassReference(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""class MyClass
       |
       |object Test {
       |  val obj: MyClass..$CARET = new MyClass
       |}""".stripMargin
  )

  @Test
  def caretOnClassOpeningBrace(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""class A {..$CARET
         |}""".stripMargin,
    resultText =
      s"""/**
         | *
         | */
         |class A {
         |}""".stripMargin
  )

  @Test
  def caretOnClassClosingBrace(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""class A {
         |}..$CARET""".stripMargin,
    resultText =
      s"""/**
         | *
         | */
         |class A {
         |}""".stripMargin
  )

  @Test
  def caretOnTraitOpeningBrace(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""trait MyTrait {..$CARET
         |}""".stripMargin,
    resultText =
      s"""/**
         | *
         | */
         |trait MyTrait {
         |}""".stripMargin
  )

  @Test
  def caretOnObjectClosingBrace(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""object MyObject {
         |}..$CARET""".stripMargin,
    resultText =
      s"""/**
         | *
         | */
         |object MyObject {
         |}""".stripMargin
  )

  @Test
  def caretOnMethodBodyClosingBrace(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(): Unit = {}..$CARET
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  /**
         |   *
         |   */
         |  def foo(): Unit = {}
         |}""".stripMargin
  )

  @Test
  def caretOnClassTypeParamClosingBracket(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""class A[T]..$CARET""".stripMargin,
    resultText =
      s"""/**
         | * @tparam T
         | */
         |class A[T]""".stripMargin
  )

  @Test
  def caretOnClassParamClosingParen(): Unit = doAddScalaDocCommandCompletionTest(
    fileText =
      s"""class A(x: Int)..$CARET""".stripMargin,
    resultText =
      s"""/**
         | * @param x
         | */
         |class A(x: Int)""".stripMargin
  )

  @Test
  def noCompletionForRegularBlockClosingBrace(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""object Test {
       |  val x = {
       |    1
       |  }..$CARET
       |}""".stripMargin
  )

  @Test
  def noCompletionForOpeningBracket(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""class A[..$CARET T]""".stripMargin
  )

  @Test
  def noCompletionForOpeningParen(): Unit = checkNoAddScalaDocCommandCompletion(
    s"""object Test {
       |  def foo(..$CARET x: Int): Int = x
       |}""".stripMargin
  )
}
