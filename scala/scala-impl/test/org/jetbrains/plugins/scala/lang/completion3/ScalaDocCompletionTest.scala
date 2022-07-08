package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._

class ScalaDocCompletionTest extends ScalaCodeInsightTestBase {

  import ScalaDocCompletionTest.DEFAULT_TIME

  def testTagNameCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         | /**
         |  * @par$CARET
         |  */
         | def f(i: Int) { }
      """.stripMargin,
    resultText =
      """
        | /**
        |  * @param
        |  */
        | def f(i: Int) { }
      """.stripMargin,
    item = "param",
    time = DEFAULT_TIME
  )

  def testTagValueCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         | /**
         |  * @param par$CARET
         |  */
         | def f(param: String) {}
      """.stripMargin,
    resultText =
      """
        | /**
        |  * @param param
        |  */
        | def f(param: String) {}
      """.stripMargin,
    item = "param",
    time = DEFAULT_TIME
  )

  def testLinkCodeCompletion(): Unit = doRawCompletionTest(
    fileText =
      s"""
         | /**
         |  *
         |  * [[HashM$CARET
         |  */
      """.stripMargin,
    resultText =
      """
        | /**
        |  *
        |  * [[java.util.HashMap
        |  */
      """.stripMargin,
    invocationCount = DEFAULT_TIME,
  ) { lookup =>
    lookup.getObject match {
      case clazz: PsiClass => clazz.qualifiedName == "java.util.HashMap"
      case _ => false
    }
  }

  def testTagValueFilteredCompletion(): Unit = doCompletionTest(
    fileText =
      s"""
         |/**
         | * @param iii
         | * @param i$CARET
         | */
         | def f(iii: Int, ikk: Int) {}
      """.stripMargin,
    resultText =
      """
        |/**
        | * @param iii
        | * @param ikk
        | */
        | def f(iii: Int, ikk: Int) {}
      """.stripMargin,
    item = "ikk",
    time = DEFAULT_TIME
  )
}

object ScalaDocCompletionTest {

  private val DEFAULT_TIME: Int = 2
}