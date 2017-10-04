package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.psi.PsiClass
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.extensions._

/**
  * User: Dmitry Naydanov
  * Date: 12/9/11
  */
class ScalaDocCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}
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

  def testLinkCodeCompletion(): Unit = doCompletionTest(
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
    char = ScalaCodeInsightTestBase.DEFAULT_CHAR,
    time = DEFAULT_TIME,
    completionType = ScalaCodeInsightTestBase.DEFAULT_COMPLETION_TYPE
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