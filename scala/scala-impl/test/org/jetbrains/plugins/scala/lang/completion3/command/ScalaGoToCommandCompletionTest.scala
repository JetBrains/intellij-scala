package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.annotations.Nullable
import org.junit.Test

final class ScalaGoToCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val GoToDeclarationPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Go to declaration")
  private val GoToSuperMethodPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Go to super method")

  private def doGoToDeclarationCommandCompletionTest(fileText: String, @Nullable resultText: String = null): Unit =
    doCommandCompletionTest(fileText, resultText = resultText, predicate = GoToDeclarationPredicate)

  private def checkNoGoToDeclarationCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = GoToDeclarationPredicate)

  private def doGoToSuperMethodCommandCompletionTest(fileText: String, @Nullable resultText: String = null): Unit =
    doCommandCompletionTest(fileText, resultText = resultText, predicate = GoToSuperMethodPredicate)

  private def checkNoGoToSuperMethodCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = GoToSuperMethodPredicate)

  @Test
  def goToParameterTypeDeclaration(): Unit = doGoToDeclarationCommandCompletionTest(
    fileText =
      s"""trait MyString
         |
         |object Test {
         |  def test(str: ${start}MyString$end.$CARET): Unit = {}
         |}
         |""".stripMargin,
    resultText =
      s"""trait MyString$CARET
         |
         |object Test {
         |  def test(str: MyString): Unit = {}
         |}
         |""".stripMargin
  )

  @Test
  def goToLocalValDeclaration(): Unit = doGoToDeclarationCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val myVal = 42
         |  val result = ${start}myVal$end.$CARET + 1
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  val myVal$CARET = 42
         |  val result = myVal + 1
         |}""".stripMargin
  )

  @Test
  def goToMethodDeclaration(): Unit = doGoToDeclarationCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def myMethod(): Int = 42
         |  def test(): Unit = { ${start}myMethod$end.$CARET }
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  def myMethod$CARET(): Int = 42
         |  def test(): Unit = { myMethod }
         |}""".stripMargin
  )

  @Test
  def goToSuperTraitDeclaration(): Unit = doGoToDeclarationCommandCompletionTest(
    fileText =
      s"""trait MySuperTrait
         |
         |class MyClass extends ${start}MySuperTrait$end.$CARET
         |""".stripMargin,
    resultText =
      s"""trait MySuperTrait$CARET
         |
         |class MyClass extends MySuperTrait
         |""".stripMargin
  )

  @Test
  def noGoToDeclarationForIntegerLiteral(): Unit =
    checkNoGoToDeclarationCommandCompletion(
      s"""object Test {
         |  val x = 1.$CARET
         |}""".stripMargin
    )

  @Test
  def noGoToDeclarationForDefinitionName(): Unit =
    checkNoGoToDeclarationCommandCompletion(
      s"""object Test {
         |  def foo.$CARET(a: Int): Int = a
         |}""".stripMargin
    )

  @Test
  def noGoToDeclarationForUnresolvedReference(): Unit =
    checkNoGoToDeclarationCommandCompletion(
      s"""object Test {
         |  val x: UndefinedType.$CARET = ???
         |}""".stripMargin
    )

  @Test
  def goToSuperMethodFromBody(): Unit = doGoToSuperMethodCommandCompletionTest(
    fileText =
      s"""trait Base {
         |  def foo(a: Int): Unit
         |}
         |
         |class Child extends Base {
         |  override def foo(a: Int): Unit = {
         |    ..$CARET
         |  }
         |}""".stripMargin,
    resultText =
      s"""trait Base {
         |  def foo$CARET(a: Int): Unit
         |}
         |
         |class Child extends Base {
         |  override def foo(a: Int): Unit = {
         |
         |  }
         |}""".stripMargin
  )

  @Test
  def goToSuperMethodFromMethodName(): Unit = doGoToSuperMethodCommandCompletionTest(
    fileText =
      s"""trait Base {
         |  def foo(a: Int): Int = a
         |}
         |
         |class Child extends Base {
         |  override def foo..$CARET(a: Int): Int = a + 1
         |}""".stripMargin,
    resultText =
      s"""trait Base {
         |  def foo$CARET(a: Int): Int = a
         |}
         |
         |class Child extends Base {
         |  override def foo(a: Int): Int = a + 1
         |}""".stripMargin
  )

  @Test
  def noGoToSuperMethodForNonOverridingMethod(): Unit = checkNoGoToSuperMethodCommandCompletion(
    fileText =
      s"""class Standalone {
         |  def foo(a: Int): Int = {
         |    ..$CARET
         |  }
         |}""".stripMargin
  )

  @Test
  def noGoToSuperMethodOutsideFunction(): Unit = checkNoGoToSuperMethodCommandCompletion(
    fileText =
      s"""trait Base {
         |  def foo(a: Int): Int = a
         |}
         |
         |class Child extends Base {
         |  ..$CARET
         |  override def foo(a: Int): Int = a + 1
         |}""".stripMargin
  )
}
