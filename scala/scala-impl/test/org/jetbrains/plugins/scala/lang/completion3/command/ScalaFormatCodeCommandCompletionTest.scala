package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.lookup.LookupElement
import junit.framework.TestCase.{assertEquals, fail}
import org.junit.Test

final class ScalaFormatCodeCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val FormatCodePredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Reformat code")

  private def doFormatCommandCompletionTest(fileText: String, resultText: String,
                                            checkPreview: IntentionPreviewInfo => Unit = _ => ()): Unit =
    doCommandCompletionTest(fileText, resultText = resultText, predicate = FormatCodePredicate, checkPreview = checkPreview)

  @Test
  def format(): Unit = {
    val resultText =
      """object Test {
         |  def foo(): Unit = {
         |    val y = 10
         |    val x = y
         |  }
         |}""".stripMargin
    doFormatCommandCompletionTest(
      fileText =
        s"""object Test {
           |  def foo(): Unit = {
           |    val y = 10
           |    ${start}val x =                           y$end.$CARET
           |  }
           |}""".stripMargin,
      resultText = resultText,
      checkPreview = {
        case diff: IntentionPreviewInfo.CustomDiff =>
          assertEquals(resultText, diff.modifiedText())
        case preview => fail(s"Custom diff preview expected, got ${preview.getClass}")
      }
    )
  }

  @Test
  def formatMethodSignatureFromBodyExpression(): Unit = doFormatCommandCompletionTest(
    fileText =
      s"""object Test {
         |  ${start}def foo(x:   Int,   y:   Int):   Int = x + y$end.$CARET
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  def foo(x: Int, y: Int): Int = x + y$CARET
         |}""".stripMargin
  )

  @Test
  def formatMethodFromBlockBodyEnd(): Unit = doFormatCommandCompletionTest(
    fileText =
      s"""object Test {
         |  ${start}def foo(x:   Int): Int = {
         |    x
         |  }$end.$CARET
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  def foo(x: Int): Int = {
         |    x
         |  }$CARET
         |}""".stripMargin
  )

  @Test
  def formatMethodFromInsideBlockBody(): Unit = doFormatCommandCompletionTest(
    fileText =
      s"""object Test {
         |  ${start}def foo(x:   Int): Int = {
         |    .$CARET
         |    x
         |  }$end
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  def foo(x: Int): Int = {
         |    $CARET
         |    x
         |  }
         |}""".stripMargin
  )

  @Test
  def formatLocalValInsideMethod(): Unit = doFormatCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def outer(): Unit = {
         |    val y = 10
         |    ${start}val result   =   y + 1$end.$CARET
         |  }
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  def outer(): Unit = {
         |    val y = 10
         |    val result = y + 1$CARET
         |  }
         |}""".stripMargin
  )

  @Test
  def formatMethodWithScalaDoc(): Unit = doFormatCommandCompletionTest(
    fileText =
      s"""object Test {
         |  $start/**
         |   *    dasd asa sadasd
         |   *       dasdas
         |   */
         |  def foo(): Unit = {
         |    println("Hello"  )
         |  }$end.$CARET
         |}""".stripMargin,
    resultText =
      s"""object Test {
         |  /**
         |   * dasd asa sadasd
         |   * dasdas
         |   */
         |  def foo(): Unit = {
         |    println("Hello")
         |  }$CARET
         |}""".stripMargin
  )

  @Test
  def formatNothing(): Unit = doFormatCommandCompletionTest(
      fileText =
        s"""object Test {
           |  def foo(): Unit = {
           |    val y = 10
           |    ${start}val x = y$end.$CARET
           |  }
           |}""".stripMargin,
      resultText =
        s"""object Test {
           |  def foo(): Unit = {
           |    val y = 10
           |    val x = y$CARET
           |  }
           |}""".stripMargin,
      checkPreview = {
        case html: IntentionPreviewInfo.Html =>
          assertEquals(CodeInsightBundle.message("command.completion.reformat.nothing"), html.content().toString)
        case preview => fail(s"Html preview expected, got ${preview.getClass}")
      }
    )
}
