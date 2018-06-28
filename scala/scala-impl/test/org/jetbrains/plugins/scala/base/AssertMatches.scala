package org.jetbrains.plugins.scala.base

import org.jetbrains.plugins.scala.annotator.Message
import org.junit.Assert

/**
  * @author Roman.Shein
  * @since 15.04.2016.
  */
trait AssertMatches extends FailableTest {
  def assertNothing[T](actual: Option[T]) {
    assertMatches(actual) {
      case Nil =>
    }
  }

  def assertMatches[T](actual: Option[T])(pattern: PartialFunction[T, Unit]) {
    actual match {
      case Some(value) =>
        Assert.assertTrue(if (shouldPass) "actual: " + value.toString else failingPassed, shouldPass == pattern.isDefinedAt(value))
      case None => Assert.assertFalse(shouldPass)
    }
  }

  def assertNothing[T](actual: T) {
    assertNothing(Some(actual))
  }

  def assertMatches[T](actual: T)(pattern: PartialFunction[T, Unit]): Unit = {
    assertMatches(Some(actual))(pattern)
  }

  def assertMessages(expected: List[Message])(actual: List[Message]): Unit = {
    assertEqualsFailable(expected.mkString("\n"), actual.mkString("\n"))
  }
}
