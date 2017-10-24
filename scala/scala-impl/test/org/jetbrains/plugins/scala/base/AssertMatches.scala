package org.jetbrains.plugins.scala.base

import org.junit.Assert

/**
  * @author Roman.Shein
  * @since 15.04.2016.
  */
trait AssertMatches {
  def assertNothing[T](actual: T) {
    assertMatches(actual) {
      case Nil =>
    }
  }

  def assertMatches[T](actual: T)(pattern: PartialFunction[T, Unit]) {
    Assert.assertTrue("actual: " + actual.toString, pattern.isDefinedAt(actual))
  }
}
