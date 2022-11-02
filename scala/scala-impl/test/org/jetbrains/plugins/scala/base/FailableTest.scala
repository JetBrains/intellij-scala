package org.jetbrains.plugins.scala.base

import org.junit.Assert

trait FailableTest {

  /**
    * A hook to allow tests that are currently failing to pass when they fail and vice versa.
    * @return
    */
  protected def shouldPass: Boolean = true

  protected def assertEqualsFailable(expected: AnyRef, actual: AnyRef): Unit = {
    if (shouldPass) Assert.assertEquals(expected, actual)
    else Assert.assertNotEquals(expected, actual)
  }

  protected val failingPassed: String = "Test has passed, but was supposed to fail"
}
