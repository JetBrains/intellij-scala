package org.jetbrains.sbt

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class FailingTest:
  @Test
  def failing(): Unit =
    fail("Intentionally failing test")
