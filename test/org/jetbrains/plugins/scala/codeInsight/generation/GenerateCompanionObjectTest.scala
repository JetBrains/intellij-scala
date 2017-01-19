package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Nikolay.Tropin
 * 8/23/13
 */
class GenerateCompanionObjectTest extends ScalaGenerateTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  override protected val handler: LanguageCodeInsightActionHandler =
    new ScalaGenerateCompanionObjectHandler

  def testInCaseClass() {
    val text = s"""case class A(x: Int, s: String) {
                 |  def foo() {}
                 |  $CARET_MARKER
                 |}"""
    checkIsNotAvailable(text)
  }

  def testCompanionObjectExist() {
    val text = s"""class A(x: Int, s: String) {
                 |  def foo() {}
                 |  $CARET_MARKER
                 |}
                 |
                 |object A {}
                 |"""
    checkIsNotAvailable(text)
  }

  def testInObject() {
    val text = s"""object A { $CARET_MARKER
                 |  def foo() {}
                 |  val bar = 1
                 |}"""
    checkIsNotAvailable(text)
  }

  def testInAnonymous() {
    val text = s"""object A {
                 |  val runnable = new Runnable {
                 |    def run() {} $CARET_MARKER
                 |  }
                 |}"""
    checkIsNotAvailable(text)
  }

  def testClass() {
    val text = s"""class A(x: Int, s: String) {
                 |  def foo() {}
                 |$CARET_MARKER
                 |}"""

    val result = s"""class A(x: Int, s: String) {
                   |  def foo() {}
                   |
                   |}
                   |
                   |object A {
                   |  $CARET_MARKER
                   |}"""
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }

  def testTrait() {
    val text = s"""trait A {
                 |  def foo() {$CARET_MARKER}
                 |
                 |}"""

    val result = s"""trait A {
                   |  def foo() {}
                   |
                   |}
                   |
                   |object A {
                   |  $CARET_MARKER
                   |}"""
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }

  def testInnerClass() {
    val text = s"""trait A {
                 |  def foo()
                 |  class B {
                 |    def bar()$CARET_MARKER = 1
                 |  }
                 |}"""
    val result = s"""trait A {
                   |  def foo()
                   |  class B {
                   |    def bar() = 1
                   |  }
                   |
                   |  object B {
                   |    $CARET_MARKER
                   |  }
                   |
                   |}"""
    performTest(text, result, checkAvailability = true, checkCaretOffset = true)
  }
}
