package org.jetbrains.plugins.scala.codeInsight.generation

/**
 * Tests for toString method generation.
 * @author Rado Buransky (buransky.com)
 */
class GenerateToStringTest extends ScalaGenerateTestBase {
  override val handler = new ScalaGenerateToStringHandler

  def testFindAllFields(): Unit = {
    val text = s"""class A (i: Int, val j: Int) {
                 |  val x = 0$CARET_MARKER
                 |  var y = 0
                 |  private val z = 0
                 |}"""
    val result = """class A (i: Int, val j: Int) {
                   |  val x = 0
                   |  var y = 0
                   |  private val z = 0
                   |
                   |  override def toString = s"A($x, $y, $z, $j)"
                   |}"""

    testInvoke(text, result, checkCaret = false)
  }

  def testEmptyClass(): Unit = {
    val text = s"""class A() {
                 |  $CARET_MARKER
                 |}"""
    val result = """class A() {
                   |
                   |  override def toString = s"A()"
                   |}"""

    testInvoke(text, result, checkCaret = false)
  }
}
