package org.jetbrains.plugins.scala.util

package object assertions {

  def failWithCause(message: String, cause: Throwable): Nothing =
    throw new AssertionError(message, cause)
}
