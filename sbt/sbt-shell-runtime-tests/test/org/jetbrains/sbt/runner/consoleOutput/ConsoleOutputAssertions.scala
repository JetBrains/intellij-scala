package org.jetbrains.sbt.runner.consoleOutput

import org.junit.Assert.{assertEquals, assertFalse, assertTrue}

private[runner] object ConsoleOutputAssertions {

  def assertContains(clue: String, output: String, expectedFragment: String): Unit =
    assertTrue(
      s"""$clue
         |Expected output fragment:
         |${expectedFragment.indent(2)}
         |Actual output:
         |${output.indent(2)}""".stripMargin,
      output.contains(expectedFragment),
    )

  def assertDoesNotContain(clue: String, output: String, unexpectedFragment: String): Unit =
    assertFalse(
      s"""$clue
         |Unexpected output fragment:
         |${unexpectedFragment.indent(2)}
         |Actual output:
         |${output.indent(2)}""".stripMargin,
      output.contains(unexpectedFragment),
    )

  def assertSbtShellWaitingHintPresence(
    output: String,
    expectedHintPresent: Boolean,
    hintText: String,
  ): Unit = {
    if (expectedHintPresent) {
      val clue = "Run configuration console output must contain the IDE-generated sbt shell waiting hint"
      assertContains(clue, output, hintText)
      assertEquals(s"$clue exactly once", 1, occurrenceCount(output, hintText))
    } else {
      val clue = "Run configuration console output must not contain the IDE-generated sbt shell waiting hint"
      assertDoesNotContain(clue, output, hintText)
    }
  }

  def assertSbtShellWaitingHintInlayPresence(
    expectedHintPresent: Boolean,
    hintText: String,
    inlayOffsetsAfterHintText: Seq[Int],
  ): Unit = {
    if (expectedHintPresent) {
      assertEquals(
        "Run configuration console must contain the sbt shell waiting hint inlay after the printed hint text exactly once",
        1,
        inlayOffsetsAfterHintText.size,
      )
    } else {
      assertTrue(
        s"""Run configuration console must not contain an sbt shell waiting hint inlay
           |Hint text:
           |${hintText.indent(2)}
           |Actual inlay offsets after hint text:
           |${inlayOffsetsAfterHintText.mkString(", ").indent(2)}""".stripMargin,
        inlayOffsetsAfterHintText.isEmpty,
      )
    }
  }

  private def occurrenceCount(output: String, fragment: String): Int =
    output.sliding(fragment.length).count(_ == fragment)
}
