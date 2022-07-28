package org.jetbrains.plugins.scala
package codeInsight
package generation
package actions

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

class GeneratePropertyTest extends ScalaGenerateTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  override protected val handler: LanguageCodeInsightActionHandler =
    new ScalaGeneratePropertyAction.Handler

  def testSimple(): Unit = {
    val text =
      s"""class A {
         |  ${CARET_MARKER}var a: Int = 0
         |}""".stripMargin

    val result =
      s"""class A {
         |  private[this] var _a: Int = 0
         |
         |  def a: Int = _a
         |
         |  def a_=(value: Int): Unit = {
         |    _a = value
         |  }
         |}""".stripMargin
    performTest(text, result, checkAvailability = true)
  }

  def testWithoutType(): Unit = {
    val text =
      s"""object A {
         |  ${CARET_MARKER}var a = 0
         |}""".stripMargin

    val result =
      s"""object A {
         |  private[this] var _a: Int = 0
         |
         |  def a: Int = _a
         |
         |  def a_=(value: Int): Unit = {
         |    _a = value
         |  }
         |}""".stripMargin
    performTest(text, result, checkAvailability = true)
  }

  def testWithModifiers(): Unit = {
    val text =
      s"""class A {
         |  protected ${CARET_MARKER}var a = 0
         |}""".stripMargin

    val result =
      s"""class A {
         |  private[this] var _a: Int = 0
         |
         |  protected def a: Int = _a
         |
         |  protected def a_=(value: Int): Unit = {
         |    _a = value
         |  }
         |}""".stripMargin
    performTest(text, result, checkAvailability = true)
  }
}
