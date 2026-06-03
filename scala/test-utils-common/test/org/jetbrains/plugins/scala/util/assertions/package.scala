package org.jetbrains.plugins.scala.util

import org.junit.Assert.fail

import scala.util.{Failure, Success, Try}

package object assertions {

  def failWithCause(message: String, cause: Throwable): Nothing =
    throw new AssertionError(message, cause)

  def assertFails(body: => Unit): Unit =
    Try(body) match {
      case Failure(_: AssertionError) => // as expected
      case Failure(exception) => throw exception
      case Success(_) =>
        fail("Test is expected to fail but is passed successfully")
    }
}
