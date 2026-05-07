package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

final class ScalaIntroduceVariableCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val IntroduceVariablePredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Introduce variable")

  private def doIntroduceVariableCommandCompletionTest(fileText: String, resultText: String): Unit =
    doCommandCompletionTest(fileText, resultText = resultText, predicate = IntroduceVariablePredicate)

  private def checkNoIntroduceVariableCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = IntroduceVariablePredicate)

  @Test
  def integerLiteral(): Unit = doIntroduceVariableCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val r = ${start}42$end..$CARET
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  private val i: Int = 42
         |  val r = i
         |}""".stripMargin
  )

  @Test
  def integerSumExpression(): Unit = doIntroduceVariableCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val r = 1 + ${start}1$end..$CARET
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  private val i: Int = 1
         |  val r = 1 + i
         |}""".stripMargin
  )

  @Test
  def methodCallReturningInt(): Unit = doIntroduceVariableCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def compute(): Int = 42
         |  val r = ${start}compute()$end..$CARET
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  def compute(): Int = 42
         |
         |  private val i: Int = compute()
         |  val r = i
         |}""".stripMargin
  )

  @Test
  def chainedMethodCall(): Unit = doIntroduceVariableCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val r = $start"abc".length$end..$CARET
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  private val length: Int = "abc".length
         |  val r = length
         |}""".stripMargin
  )

  @Test
  def noIntroduceVariableForClassName(): Unit = checkNoIntroduceVariableCommandCompletion(
    s"""class MyClass..$CARET(val x: Int)"""
  )

  @Test
  def noIntroduceVariableForMethodName(): Unit = checkNoIntroduceVariableCommandCompletion(
    s"""object Test {
       |  def myMethod..$CARET(x: Int): Int = x + 1
       |}""".stripMargin
  )

  @Test
  def noIntroduceVariableForValName(): Unit = checkNoIntroduceVariableCommandCompletion(
    s"""object Test {
       |  val x..$CARET = 42
       |}""".stripMargin
  )

  @Test
  def noIntroduceVariableForImportStatement(): Unit = checkNoIntroduceVariableCommandCompletion(
    s"""import java.util.ArrayList.$CARET
       |
       |class Test""".stripMargin
  )

  @Test
  def noIntroduceVariableForClosingBraceInTypeDefinition(): Unit = checkNoIntroduceVariableCommandCompletion(
    s"""class Test {
       |  val x = 1
       |}.$CARET""".stripMargin
  )
}
