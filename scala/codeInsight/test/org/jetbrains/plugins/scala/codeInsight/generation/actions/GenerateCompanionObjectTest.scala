package org.jetbrains.plugins.scala
package codeInsight
package generation
package actions

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

class GenerateCompanionObjectTest extends ScalaGenerateTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  override protected val handler: LanguageCodeInsightActionHandler =
    new ScalaGenerateCompanionObjectAction.Handler

  def testInCaseClass(): Unit = {
    val text =
      s"""case class A(x: Int, s: String) {
         |  def foo() {}
         |  $CARET_MARKER
         |}""".stripMargin
    checkIsNotAvailable(text)
  }

  def testCompanionObjectExist(): Unit = {
    val text =
      s"""class A(x: Int, s: String) {
         |  def foo() {}
         |  $CARET_MARKER
         |}
         |
         |object A {}
         |""".stripMargin
    checkIsNotAvailable(text)
  }

  def testInObject(): Unit = {
    val text =
      s"""object A { $CARET_MARKER
         |  def foo() {}
         |  val bar = 1
         |}""".stripMargin
    checkIsNotAvailable(text)
  }

  def testInAnonymous(): Unit = {
    val text =
      s"""object A {
         |  val runnable = new Runnable {
         |    def run() {} $CARET_MARKER
         |  }
         |}""".stripMargin
    checkIsNotAvailable(text)
  }

  def testClass(): Unit = {
    val text =
      s"""class A(x: Int, s: String) {
         |  def foo() {}
         |$CARET_MARKER
         |}""".stripMargin

    val result =
      s"""class A(x: Int, s: String) {
         |  def foo() {}
         |
         |}
         |
         |object A {
         |  $CARET_MARKER
         |}""".stripMargin
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }

  def testTrait(): Unit = {
    val text =
      s"""trait A {
         |  def foo() {$CARET_MARKER}
         |
         |}""".stripMargin

    val result =
      s"""trait A {
         |  def foo() {}
         |
         |}
         |
         |object A {
         |  $CARET_MARKER
         |}""".stripMargin
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }

  def testInnerClass(): Unit = {
    val text =
      s"""trait A {
         |  def foo()
         |  class B {
         |    def bar()$CARET_MARKER = 1
         |  }
         |}""".stripMargin
    val result =
      s"""trait A {
         |  def foo()
         |  class B {
         |    def bar() = 1
         |  }
         |
         |  object B {
         |    $CARET_MARKER
         |  }
         |}""".stripMargin
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }
}
