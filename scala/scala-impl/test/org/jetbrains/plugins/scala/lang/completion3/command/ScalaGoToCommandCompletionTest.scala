package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.annotations.Nullable
import org.junit.Test

final class ScalaGoToCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val GoToDeclarationPredicate: LookupElement => Boolean = lookupStringContains(_, "Go to declaration")

  private def doGoToDeclarationCommandCompletionTest(fileText: String, @Nullable resultText: String = null): Unit =
    doCommandCompletionTest(fileText, resultText = resultText, predicate = GoToDeclarationPredicate)

  private def checkNoGoToDeclarationCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = GoToDeclarationPredicate)

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
}
