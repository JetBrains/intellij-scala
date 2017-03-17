package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaSmartAnonymousFunctionCompletionTest extends ScalaCodeInsightTestBase {
  def testAbstractTypeInfoFromFirstClause() {
    val fileText =
"""
def foo[T](x: T)(y: T => Int) = 1
foo(2)(<caret>)
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
"""
def foo[T](x: T)(y: T => Int) = 1
foo(2)((i: Int) => <caret>)
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testSimpleCaseTest() {
    val fileText =
"""
def foo(x: String => String) = 1
foo {<caret>}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
"""
def foo(x: String => String) = 1
foo {case str: String => <caret>}
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testSimple() {
    val fileText =
"""
def foo(x: String => String) = 1
foo(<caret>)
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
"""
def foo(x: String => String) = 1
foo((str: String) => <caret>)
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testJustTuple() {
    val fileText =
"""
def foo(x: Tuple2[Int, Int] => Int) = 1
foo(<caret>)
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
"""
def foo(x: Tuple2[Int, Int] => Int) = 1
foo((tuple: (Int, Int)) => <caret>)
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testCaseTuple() {
    val fileText =
"""
def foo(x: Tuple2[Int, Int] => Int) = 1
foo{<caret>}
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
"""
def foo(x: Tuple2[Int, Int] => Int) = 1
foo{case (i: Int, i0: Int) => <caret>}
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testAbstractTypeInfoWithUpper() {
    val fileText =
"""
def foo[T <: Runnable](x: (T, String) => String) = 1
foo(<caret>)
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
"""
def foo[T <: Runnable](x: (T, String) => String) = 1
foo((value: Runnable, str: String) => <caret>)
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testAbstractTypeInfoWithLower() {
    val fileText =
"""
def foo[T >: Int](x: (T, String) => String) = 1
foo(<caret>)
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
"""
def foo[T >: Int](x: (T, String) => String) = 1
foo((value: Int, str: String) => <caret>)
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testAbstractTypeInfoTypeParameters() {
    val fileText =
"""
def foo[T <: Runnable](x: T => String) = 1
class X extends Runnable
foo[X](<caret>)
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
"""
def foo[T <: Runnable](x: T => String) = 1
class X extends Runnable
foo[X]((x: X) => <caret>)
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }


  def testFewParams() {
    val fileText =
      """
      |def foo(c: (Int, Int, Int, Int) => Int) = 1
      |foo(<caret>)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
      """
      |def foo(c: (Int, Int, Int, Int) => Int) = 1
      |foo((i: Int, i0: Int, i1: Int, i2: Int) => <caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testFewParamsDifferent() {
    val fileText =
      """
      |def foo(x: (Int, String, Int, String) => Int) = 1
      |foo(<caret>)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
      """
        |def foo(x: (Int, String, Int, String) => Int) = 1
        |foo((i: Int, str: String, i0: Int, str0: String) => <caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testAbstractTypeInfo() {
    val fileText =
      """
      |def foo[T](x: (T, String) => String) = 1
      |foo(<caret>)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
      """
        |def foo[T](x: (T, String) => String) = 1
        |foo((value: T, str: String) => <caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testAliasType() {
    val fileText =
      """
      |type T = Int => String
      |def zoo(p: T) {}
      |zoo(<caret>)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
      """
      |type T = Int => String
      |def zoo(p: T) {}
      |zoo((i: Int) => <caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }
}