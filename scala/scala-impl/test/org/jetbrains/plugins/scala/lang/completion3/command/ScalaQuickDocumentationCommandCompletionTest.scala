package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

final class ScalaQuickDocumentationCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val QuickDocPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Quick documentation")

  private def doQuickDocCommandCompletionTest(fileText: String): Unit =
    doCommandCompletionTest(fileText, predicate = QuickDocPredicate, finishLookup = false)

  private def checkNoQuickDocCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = QuickDocPredicate)

  @Test
  def className(): Unit = doQuickDocCommandCompletionTest(
    s"""class ${start}MyClass$end..$CARET(val x: Int)"""
  )

  @Test
  def traitName(): Unit = doQuickDocCommandCompletionTest(
    s"""trait ${start}MyTrait$end..$CARET {
       |  def method(): Unit
       |}""".stripMargin
  )

  @Test
  def objectName(): Unit = doQuickDocCommandCompletionTest(
    s"""object ${start}MyObject$end..$CARET {
       |  val value = 1
       |}""".stripMargin
  )

  @Test
  def caseClassName(): Unit = doQuickDocCommandCompletionTest(
    s"""case class ${start}Point$end..$CARET(x: Int, y: Int)"""
  )

  @Test
  def methodDeclaration(): Unit = doQuickDocCommandCompletionTest(
    s"""object Test {
       |  def ${start}myMethod$end..$CARET(x: Int): Int = x + 1
       |}""".stripMargin
  )

  @Test
  def typeAliasDeclaration(): Unit = doQuickDocCommandCompletionTest(
    s"""object Test {
       |  type ${start}MyType$end..$CARET = Int
       |}""".stripMargin
  )

  @Test
  def classConstructorParameter(): Unit = doQuickDocCommandCompletionTest(
    s"""class MyClass(${start}x$end..$CARET: Int)"""
  )

  @Test
  def classReference(): Unit = doQuickDocCommandCompletionTest(
    s"""class MyClass(val x: Int)
       |
       |object Test {
       |  val obj: ${start}MyClass$end..$CARET = new MyClass(1)
       |}""".stripMargin
  )

  @Test
  def classReferenceInNew(): Unit = doQuickDocCommandCompletionTest(
    s"""class MyClass(val x: Int)
       |
       |object Test {
       |  val obj = new ${start}MyClass$end..$CARET(1)
       |}""".stripMargin
  )

  @Test
  def methodCallReference(): Unit = doQuickDocCommandCompletionTest(
    s"""object Test {
       |  def myMethod(x: Int): Int = x + 1
       |  val result = ${start}myMethod$end..$CARET(10)
       |}""".stripMargin
  )

  @Test
  def traitReferenceInExtends(): Unit = doQuickDocCommandCompletionTest(
    s"""trait MyTrait
       |
       |class Child extends ${start}MyTrait$end..$CARET""".stripMargin
  )

  @Test
  def typeAliasReference(): Unit = doQuickDocCommandCompletionTest(
    s"""object Test {
       |  type MyType = Int
       |  val x: ${start}MyType$end..$CARET = 1
       |}""".stripMargin
  )

  @Test
  def objectReference(): Unit = doQuickDocCommandCompletionTest(
    s"""object MyObject {
       |  val value = 1
       |}
       |
       |object Test {
       |  val x = ${start}MyObject$end..$CARET.value
       |}""".stripMargin
  )

  @Test
  def javaClassReference(): Unit = doQuickDocCommandCompletionTest(
    s"""object Test {
       |  val list: java.util.${start}ArrayList$end..$CARET[String] = new java.util.ArrayList[String]()
       |}""".stripMargin
  )

  @Test
  def packageReference(): Unit = doQuickDocCommandCompletionTest(
    s"""import java.${start}util$end..$CARET.ArrayList
       |
       |class Test""".stripMargin
  )

  @Test
  def noQuickDocForLiteral(): Unit = checkNoQuickDocCommandCompletion(
    s"""object Test {
       |  val x = 42.$CARET
       |}""".stripMargin
  )

  @Test
  def noQuickDocForClosingBrace(): Unit = checkNoQuickDocCommandCompletion(
    s"""object Test {
       |  def foo() = {}.$CARET
       |}""".stripMargin
  )

  @Test
  def noQuickDocForClosingParen(): Unit = checkNoQuickDocCommandCompletion(
    s"""object Test {
       |  def foo(x: Int) = x
       |  foo(1).$CARET
       |}""".stripMargin
  )
}
