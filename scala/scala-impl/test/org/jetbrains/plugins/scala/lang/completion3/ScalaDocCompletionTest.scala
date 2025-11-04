package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.junit.Assert.assertEquals
import org.junit.Test

class ScalaDocCompletionTest extends ScalaCompletionTestBase {

  private final val DefaultInvocationCount = 2

  @Test
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

  @Test
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

  private def assertCompletionItemsEqual(
    code: String,
    expectedLookups: Seq[String]
  ): Unit = {
    val (_, items) = activeLookupWithItems(code)
    val actualLookups = items.map(_.getLookupString)
    assertEquals("Completion lookup items don't match", expectedLookups, actualLookups)
  }

  @Test
  def testParameterTagAvailableItems(): Unit = assertCompletionItemsEqual(
    s"""/**
       | * @param $CARET
       | */
       |def bar[TypeParam1, TypeParam2, TypeParam3](param1: AnyRef, param2: AnyRef, param3: AnyRef): Unit = ()
       |""".stripMargin,
    Seq("param1", "param2", "param3")
  )

  @Test
  def testTypeParameterTagAvailableItems(): Unit = assertCompletionItemsEqual(
    s"""/**
       | * @tparam $CARET
       | */
       |def bar[TypeParam1, TypeParam2, TypeParam3](param1: AnyRef, param2: AnyRef, param3: AnyRef): Unit = ()
       |""".stripMargin,
    Seq("TypeParam1", "TypeParam2", "TypeParam3")
  )

  @Test
  def testParameterTagAvailableItems_DontIncludeParamsWithAlreadyExistingTag(): Unit = assertCompletionItemsEqual(
    s"""/**
       | * @param param1
       | * @param param3  dummy description
       | * @param $CARET
       | */
       |def bar[TypeParam1, TypeParam2, TypeParam3](param1: AnyRef, param2: AnyRef, param3: AnyRef): Unit = ()
       |""".stripMargin,
    Seq("param2")
  )

  @Test
  def testTypeParameterTagAvailableItems_DontIncludeParamsWithAlreadyExistingTag(): Unit = assertCompletionItemsEqual(
    s"""/**
       | * @tparam TypeParam1
       | * @tparam TypeParam3 dummy description
       | * @tparam $CARET
       | */
       |def bar[TypeParam1, TypeParam2, TypeParam3](param1: AnyRef, param2: AnyRef, param3: AnyRef): Unit = ()
       |""".stripMargin,
    Seq("TypeParam2")
  )

  /*@Test
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
  }*/

  @Test
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
