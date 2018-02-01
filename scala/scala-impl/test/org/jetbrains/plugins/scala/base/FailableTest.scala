package org.jetbrains.plugins.scala.base

trait FailableTest {
  /**
    * A hook to allow tests that are currently failing to pass when they fail and vice versa.
    * @return
    */
  protected def shouldPass: Boolean = true

  val failingPassed: String = "Test has passed, but was supposed to fail"
}
