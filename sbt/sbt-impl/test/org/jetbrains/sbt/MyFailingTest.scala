package org.jetbrains.sbt

import org.junit.Assert.fail
import org.junit.Test

class MyFailingTest:
  @Test
  def failingTest(): Unit =
    fail("Intentionally failing")
