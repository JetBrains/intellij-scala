package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase

class ScalaDocCompletionTest extends ScalaCompletionTestBase {

  import ScalaDocCompletionTest.DefaultInvocationCount

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
    invocationCount = DefaultInvocationCount
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
    invocationCount = DefaultInvocationCount
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
    invocationCount = DefaultInvocationCount,
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
    invocationCount = DefaultInvocationCount
  )
}

object ScalaDocCompletionTest {

  private val DefaultInvocationCount: Int = 2
}