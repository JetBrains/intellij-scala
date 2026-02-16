package org.jetbrains.sbt

import org.junit.Assert.fail
import org.junit.Test

class FailingTest:
  @Test
  def failing(): Unit =
    fail("Intentionally failing test")
