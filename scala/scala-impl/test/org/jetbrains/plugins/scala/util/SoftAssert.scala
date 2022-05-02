package org.jetbrains.plugins.scala.util

import org.apache.commons.lang3.exception.ExceptionUtils
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert

import scala.util.control.NoStackTrace

class SoftAssert {
  private var errors = Seq.empty[AssertionError]

  final def assertAll(): Unit =
    if (errors.nonEmpty) {
      val stackTraces = errors.map(ExceptionUtils.getStackTrace)
      val errorMessage = stackTraces.mkString("\n")
      throw new AssertionError(errorMessage) with NoStackTrace
    }

  final protected def assertThat[A](reason: String,
                                    actual: A,
                                    expected: Matcher[_ >: A]): Unit =
    catchError(MatcherAssert.assertThat(reason, actual, expected))

  // other assert methods...

  private def catchError(action: => Unit): Unit =
    try {
      action
    } catch {
      case assertionError: AssertionError =>
        errors :+= assertionError
    }
}
