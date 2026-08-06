package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

abstract class ScalaOverrideImplementMethodsCommandCompletionTest(commandLookupPrefix: String) extends ScalaCommandCompletionTestBase {
  private val CommandCompletionPredicate: LookupElement => Boolean = lookupStringStartsWith(_, commandLookupPrefix)

  private def doOverrideImplementMethodsCommandCompletionTest(fileText: String): Unit =
    doCommandCompletionTest(fileText, predicate = CommandCompletionPredicate, finishLookup = false)

  private def checkNoOverrideImplementMethodsCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, CommandCompletionPredicate)

  @Test
  def classNameWithParent(): Unit = doOverrideImplementMethodsCommandCompletionTest(
    s"""trait Base {
       |  def foo: Int
       |  def bar: Int = 42
       |}
       |
       |class Child..$CARET extends Base""".stripMargin
  )

  @Test
  def classConstructorClauseWithParent(): Unit = doOverrideImplementMethodsCommandCompletionTest(
    s"""trait Base {
       |  def foo: Int
       |  def bar: Int = 42
       |}
       |
       |class Child(x: Int)..$CARET extends Base""".stripMargin
  )

  @Test
  def classOpeningBraceWithParent(): Unit = doOverrideImplementMethodsCommandCompletionTest(
    s"""trait Base {
       |  def foo: Int
       |  def bar: Int = 42
       |}
       |
       |class Child extends Base {..$CARET
       |}""".stripMargin
  )

  @Test
  def classClosingBraceWithParent(): Unit = doOverrideImplementMethodsCommandCompletionTest(
    s"""trait Base {
       |  def foo: Int
       |  def bar: Int = 42
       |}
       |
       |class Child extends Base {
       |}..$CARET""".stripMargin
  )

  @Test
  def traitNameWithParent(): Unit = doOverrideImplementMethodsCommandCompletionTest(
    s"""trait Base {
       |  def foo: Int
       |  def bar: Int = 42
       |}
       |
       |trait Child..$CARET extends Base""".stripMargin
  )

  @Test
  def objectNameWithParent(): Unit = doOverrideImplementMethodsCommandCompletionTest(
    s"""trait Base {
       |  def foo: Int
       |  def bar: Int = 42
       |}
       |
       |object Child..$CARET extends Base""".stripMargin
  )

  @Test
  def objectOpeningBraceWithParent(): Unit = doOverrideImplementMethodsCommandCompletionTest(
    s"""trait Base {
       |  def foo: Int
       |  def bar: Int = 42
       |}
       |
       |object Child extends Base {..$CARET
       |}""".stripMargin
  )

  @Test
  def noCompletionForClassWithoutParent(): Unit = checkNoOverrideImplementMethodsCommandCompletion(
    s"""class Child..$CARET"""
  )

  @Test
  def noCompletionForObjectWithoutParent(): Unit = checkNoOverrideImplementMethodsCommandCompletion(
    s"""object Child..$CARET"""
  )

  @Test
  def noCompletionForMethodName(): Unit = checkNoOverrideImplementMethodsCommandCompletion(
    s"""trait Base {
       |  def foo: Int
       |  def bar: Int = 42
       |}
       |
       |class Child extends Base {
       |  def baz..$CARET: Int = 1
       |}""".stripMargin
  )
}

final class ScalaOverrideMethodsCommandCompletionTest extends ScalaOverrideImplementMethodsCommandCompletionTest("Override methods")

final class ScalaImplementMethodsCommandCompletionTest extends ScalaOverrideImplementMethodsCommandCompletionTest("Implement methods")

