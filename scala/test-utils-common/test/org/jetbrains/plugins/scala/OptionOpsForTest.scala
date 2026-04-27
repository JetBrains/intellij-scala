package org.jetbrains.plugins.scala

import org.junit.Assert.fail

object OptionOpsForTest {
  def failWith[A](message: => String): A =
    fail(message).asInstanceOf[Nothing]

  final implicit class OptionOpsForTestExt[A](private val option: Option[A]) extends AnyVal {
    def getOrFailTest(message: => String): A =
      option.getOrElse(failWith(message))

    def getOrFail(message: => String): A =
      getOrFailTest(message)
  }
}
