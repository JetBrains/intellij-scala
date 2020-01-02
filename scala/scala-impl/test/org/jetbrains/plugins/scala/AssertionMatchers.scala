package org.jetbrains.plugins.scala

import org.junit.Assert._

trait AssertionMatchers {
  implicit class AssertMatchersExt(val actual: Any) {
    def shouldBe(expected: Any): Unit = (actual, expected) match {
      case (actual: Boolean, expected: Boolean) => assertEquals(expected, actual)
      case (actual: Byte, expected: Byte) => assertEquals(expected, actual)
      case (actual: Char, expected: Char) => assertEquals(expected, actual)
      case (actual: Short, expected: Short) => assertEquals(expected, actual)
      case (actual: Int, expected: Int) => assertEquals(expected, actual)
      case (actual: Long, expected: Long) => assertEquals(expected, actual)
      case (actual: Double, expected: Double) => assertEquals(expected, actual, 0.01)
      case (actual: Float, expected: Float) => assertEquals(expected, actual, 0.01)
      case (actual: String, expected: String) => assertEquals(expected, actual)
      case (actual: Int, expected: Int) => assertEquals(expected, actual)
      case (actual, expected) => assertEquals(expected, actual)
    }

    def shouldNotBe(notExpected: Any): Unit = (actual, notExpected) match {
      case (actual: Boolean, notExpected: Boolean) => assertNotEquals(notExpected, actual)
      case (actual: Byte, notExpected: Byte) => assertNotEquals(notExpected, actual)
      case (actual: Char, notExpected: Char) => assertNotEquals(notExpected, actual)
      case (actual: Short, notExpected: Short) => assertNotEquals(notExpected, actual)
      case (actual: Int, notExpected: Int) => assertNotEquals(notExpected, actual)
      case (actual: Long, notExpected: Long) => assertNotEquals(notExpected, actual)
      case (actual: Double, notExpected: Double) => assertNotEquals(notExpected, actual, 0.01)
      case (actual: Float, notExpected: Float) => assertNotEquals(notExpected, actual, 0.01)
      case (actual: String, notExpected: String) => assertNotEquals(notExpected, actual)
      case (actual: Int, notExpected: Int) => assertNotEquals(notExpected, actual)
      case (actual, notExpected) => assertNotEquals(notExpected, actual)
    }
  }
}
