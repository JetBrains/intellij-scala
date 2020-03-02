package org.jetbrains.plugins.scala.util

import org.hamcrest.Matcher
import org.junit.Assert

class SoftAssert {
  private var errors = Seq.empty[AssertionError]

  final def assertAll(): Unit =
    if (errors.nonEmpty)
      throw new AssertionError(errors.mkString("\n\n"))

  final protected def assertThat[A](reason: String,
                                    actual: A,
                                    expected: Matcher[_ >: A]): Unit =
    catchError(Assert.assertThat(reason, actual, expected))

  // other assert methods...

  private def catchError(action: => Unit): Unit =
    try {
      action
    } catch {
      case assertionError: AssertionError =>
        errors :+= assertionError
    }
}
