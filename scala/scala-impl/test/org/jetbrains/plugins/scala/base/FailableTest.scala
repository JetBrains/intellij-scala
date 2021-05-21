package org.jetbrains.plugins.scala.base

import org.junit.Assert

trait FailableTest {

  private var _shouldPass = true
  /**
    * A hook to allow tests that are currently failing to pass when they fail and vice versa.
    * @return
    */
  protected def shouldPass: Boolean = _shouldPass

  protected def assertEqualsFailable(expected: AnyRef, actual: AnyRef): Unit = {
    if (shouldPass) Assert.assertEquals(expected, actual)
    else Assert.assertNotEquals(expected, actual)
  }

  protected def failing(body: => Unit): Unit = {
    _shouldPass = false
    var success = false
    try {
      body
      success = true
    }
    catch {
      case _: AssertionError => //this is fine
    }
    finally {
      _shouldPass = true
    }
    if (success)
      throw new AssertionError(failingPassed)
  }

  val failingPassed: String = "Test has passed, but was supposed to fail"
}
