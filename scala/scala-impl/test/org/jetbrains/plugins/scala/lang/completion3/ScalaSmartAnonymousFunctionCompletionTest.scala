package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType.SMART
import com.intellij.testFramework.EditorTestUtil

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaSmartAnonymousFunctionCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testAbstractTypeInfoFromFirstClause(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo[T](x: T)(y: T => Int) = 1
         |foo(2)($CARET)
      """.stripMargin,
    resultText =
      s"""
         |def foo[T](x: T)(y: T => Int) = 1
         |foo(2)((i: Int) => $CARET)
      """.stripMargin
  )

  def testSimpleCaseTest(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: String => String) = 1
         |foo {$CARET}
       """.stripMargin,
    resultText =
      s"""
         |def foo(x: String => String) = 1
         |foo {case str: String => $CARET}
       """.stripMargin
  )

  def testSimple(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: String => String) = 1
         |foo($CARET)
       """.stripMargin,
    resultText =
      s"""
         |def foo(x: String => String) = 1
         |foo((str: String) => $CARET)
       """.stripMargin
  )

  def testJustTuple(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Tuple2[Int, Int] => Int) = 1
         |foo($CARET)
       """.stripMargin,
    resultText =
      s"""
         |def foo(x: Tuple2[Int, Int] => Int) = 1
         |foo((tuple: (Int, Int)) => $CARET)
       """.stripMargin
  )

  def testCaseTuple(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: Tuple2[Int, Int] => Int) = 1
         |foo{$CARET}
       """.stripMargin,
    resultText =
      s"""
         |def foo(x: Tuple2[Int, Int] => Int) = 1
         |foo{case (i: Int, i0: Int) => $CARET}
       """.stripMargin
  )

  def testAbstractTypeInfoWithUpper(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo[T <: Runnable](x: (T, String) => String) = 1
         |foo($CARET)
       """.stripMargin,
    resultText =
      s"""
         |def foo[T <: Runnable](x: (T, String) => String) = 1
         |foo((value: Runnable, str: String) => $CARET)
       """.stripMargin
  )

  def testAbstractTypeInfoWithLower(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo[T >: Int](x: (T, String) => String) = 1
         |foo($CARET)
       """.stripMargin,
    resultText =
      s"""
         |def foo[T >: Int](x: (T, String) => String) = 1
         |foo((value: Int, str: String) => $CARET)
       """.stripMargin
  )

  def testAbstractTypeInfoTypeParameters(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo[T <: Runnable](x: T => String) = 1
         |class X extends Runnable
         |foo[X]($CARET)
       """.stripMargin,
    resultText =
      s"""
         |def foo[T <: Runnable](x: T => String) = 1
         |class X extends Runnable
         |foo[X]((x: X) => $CARET)
       """.stripMargin
  )

  def testFewParams(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(c: (Int, Int, Int, Int) => Int) = 1
         |foo($CARET)
       """.stripMargin,
    resultText =
      s"""
         |def foo(c: (Int, Int, Int, Int) => Int) = 1
         |foo((i: Int, i0: Int, i1: Int, i2: Int) => $CARET)
       """.stripMargin
  )

  def testFewParamsDifferent(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo(x: (Int, String, Int, String) => Int) = 1
         |foo($CARET)
       """.stripMargin,
    resultText =
      s"""
         |def foo(x: (Int, String, Int, String) => Int) = 1
         |foo((i: Int, str: String, i0: Int, str0: String) => $CARET)
       """.stripMargin
  )

  def testAbstractTypeInfo(): Unit = doCompletionTest(
    fileText =
      s"""
         |def foo[T](x: (T, String) => String) = 1
         |foo($CARET)
       """.stripMargin,
    resultText =
      s"""
         |def foo[T](x: (T, String) => String) = 1
         |foo((value: T, str: String) => $CARET)
       """.stripMargin
  )

  def testAliasType(): Unit = doCompletionTest(
    fileText =
      s"""
         |type T = Int => String
         |def zoo(p: T) {}
         |zoo($CARET)
      """.stripMargin,
    resultText =
      s"""
         |type T = Int => String
         |def zoo(p: T) {}
         |zoo((i: Int) => $CARET)
      """.stripMargin
  )

  private def doCompletionTest(fileText: String, resultText: String): Unit =
    doCompletionTest(fileText, resultText, item = "", time = 2, completionType = SMART)
}