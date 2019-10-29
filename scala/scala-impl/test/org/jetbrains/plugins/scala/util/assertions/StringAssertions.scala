package org.jetbrains.plugins.scala.util.assertions

import org.junit.Assert._

trait StringAssertions {

  def assertStartsWith(string: String, prefix: String): Unit =
    if (!string.startsWith(prefix)) {
      fail(
        s"""input string doesn't start with expected prefix
           |prefix: `${display(prefix)}`
           |input: `${display(string)}`
           |""".stripMargin
      )
    }

  def assertEndsWith(string: String, suffix: String): Unit =
    if (!string.endsWith(suffix)) {
      fail(
        s"""input string doesn't end  with expected suffix
           |suffix: `${display(suffix)}`
           |input: `${display(string)}`
           |""".stripMargin
      )
    }

  private def display(str: String): String =
    str.replace("\\n", "\\\\n")
      .replace("\\r", "\\\\r")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
}

object StringAssertions extends StringAssertions
