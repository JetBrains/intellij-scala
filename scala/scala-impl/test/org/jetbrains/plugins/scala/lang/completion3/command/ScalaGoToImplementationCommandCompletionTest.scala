package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

final class ScalaGoToImplementationCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val GoToImplPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Go to implementation")

  private def doGoToImplementationCommandCompletionTest(fileText: String): Unit =
    doCommandCompletionTest(fileText, predicate = GoToImplPredicate, finishLookup = false)

  private def doGoToImplementationCommandCompletionTest(fileText: String, resultText: String): Unit =
    doCommandCompletionTest(fileText, resultText = resultText, predicate = GoToImplPredicate)

  private def checkNoGoToImplementationCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = GoToImplPredicate)

  @Test
  def traitWithImplementingClass(): Unit = doGoToImplementationCommandCompletionTest(
    s"""trait MyTrait..$CARET {
       |  def method(): Unit
       |}
       |
       |class MyImpl extends MyTrait {
       |  override def method(): Unit = ()
       |}""".stripMargin,
    s"""trait MyTrait {
       |  def method(): Unit
       |}
       |
       |class MyImpl$CARET extends MyTrait {
       |  override def method(): Unit = ()
       |}""".stripMargin
  )

  @Test
  def abstractClassWithSubclass(): Unit = doGoToImplementationCommandCompletionTest(
    s"""abstract class Base..$CARET {
       |  def method(): Unit
       |}
       |
       |class Sub extends Base {
       |  override def method(): Unit = ()
       |}""".stripMargin
  )

  @Test
  def classWithSubclass(): Unit = doGoToImplementationCommandCompletionTest(
    s"""class Base..$CARET(val x: Int)
       |
       |class Sub(x: Int) extends Base(x)""".stripMargin
  )

  @Test
  def methodInTraitWithImpl(): Unit = doGoToImplementationCommandCompletionTest(
    s"""trait MyTrait {
       |  def foo..$CARET(): Int
       |}
       |
       |class MyImpl extends MyTrait {
       |  override def foo(): Int = 42
       |}""".stripMargin,
    s"""trait MyTrait {
       |  def foo(): Int
       |}
       |
       |class MyImpl extends MyTrait {
       |  override def foo$CARET(): Int = 42
       |}""".stripMargin
  )

  @Test
  def methodInAbstractClassWithSubclass(): Unit = doGoToImplementationCommandCompletionTest(
    s"""abstract class Base {
       |  def foo..$CARET(): Int
       |}
       |
       |class Sub extends Base {
       |  override def foo(): Int = 42
       |}""".stripMargin
  )

  @Test
  def methodInOpenClassWithSubclass(): Unit = doGoToImplementationCommandCompletionTest(
    s"""class Base {
       |  def foo..$CARET(): Int = 0
       |}
       |
       |class Sub extends Base {
       |  override def foo(): Int = 42
       |}""".stripMargin
  )

  @Test
  def noGoToImplementationForFinalClass(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""final class MyClass..$CARET(val x: Int)""".stripMargin
  )

  @Test
  def noGoToImplementationForClassWithoutSubclass(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""class MyClass..$CARET(val x: Int)""".stripMargin
  )

  @Test
  def noGoToImplementationForTraitWithoutImpl(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""trait MyTrait..$CARET {
       |  def method(): Unit
       |}""".stripMargin
  )

  @Test
  def noGoToImplementationForFinalMethod(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""class Base {
       |  final def foo..$CARET(): Int = 42
       |}
       |
       |class Sub extends Base""".stripMargin
  )

  @Test
  def noGoToImplementationForMethodInFinalClass(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""final class MyClass {
       |  def foo..$CARET(): Int = 42
       |}""".stripMargin
  )

  @Test
  def noGoToImplementationForMethodInClassWithoutSubclass(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""class MyClass {
       |  def foo..$CARET(): Int = 42
       |}""".stripMargin
  )

  @Test
  def noGoToImplementationForObjectName(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""object MyObject..$CARET {
       |  val value = 1
       |}""".stripMargin
  )

  @Test
  def noGoToImplementationForValDeclaration(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""object Test {
       |  val x..$CARET = 42
       |}""".stripMargin
  )

  @Test
  def noGoToImplementationForTypeAlias(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""object Test {
       |  type MyType..$CARET = Int
       |}""".stripMargin
  )

  @Test
  def noGoToImplementationForIntegerLiteral(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""object Test {
       |  val x = 42.$CARET
       |}""".stripMargin
  )

  @Test
  def noGoToImplementationForStringLiteral(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""object Test {
       |  val x = "hello".$CARET
       |}""".stripMargin
  )

  @Test
  def noGoToImplementationForThisKeyword(): Unit = checkNoGoToImplementationCommandCompletion(
    s"""class Test {
       |  def foo(): Test = this.$CARET
       |}""".stripMargin
  )
}
