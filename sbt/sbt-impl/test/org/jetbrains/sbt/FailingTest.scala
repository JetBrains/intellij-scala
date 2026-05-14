package org.jetbrains.sbt

import org.junit.Assert.fail
import org.junit.jupiter.api.Test

class FailingTest:
  @Test
  def failing(): Unit =
    fail("Boom!")
