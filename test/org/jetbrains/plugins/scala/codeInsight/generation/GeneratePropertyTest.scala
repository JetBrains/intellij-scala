package org.jetbrains.plugins.scala
package codeInsight.generation

import com.intellij.lang.LanguageCodeInsightActionHandler

/**
 * Nikolay.Tropin
 * 2014-09-22
 */
class GeneratePropertyTest extends ScalaGenerateTestBase {
  override val handler: LanguageCodeInsightActionHandler = new ScalaGeneratePropertyHandler

  def testSimple() {
    val text = s"""class A {
                 |  ${CARET_MARKER}var a: Int = 0
                 |}"""

    val result = s"""class A {
                   |  private[this] var _a: Int = 0
                   |
                   |  def a: Int = _a
                   |
                   |  def a_=(value: Int): Unit = {
                   |    _a = value
                   |  }
                   |}"""
    checkIsAvailable(text)
    testInvoke(text, result, checkCaret = false)
  }

  def testWithoutType() {
    val text = s"""object A {
                 |  ${CARET_MARKER}var a = 0
                 |}"""

    val result = s"""object A {
                   |  private[this] var _a: Int = 0
                   |
                   |  def a: Int = _a
                   |
                   |  def a_=(value: Int): Unit = {
                   |    _a = value
                   |  }
                   |}"""
    checkIsAvailable(text)
    testInvoke(text, result, checkCaret = false)
  }

  def testWithModifiers(): Unit = {
    val text = s"""class A {
                 |  protected ${CARET_MARKER}var a = 0
                 |}"""

    val result = s"""class A {
                   |  private[this] var _a: Int = 0
                   |
                   |  protected def a: Int = _a
                   |
                   |  protected def a_=(value: Int): Unit = {
                   |    _a = value
                   |  }
                   |}"""
    checkIsAvailable(text)
    testInvoke(text, result, checkCaret = false)
  }
}
