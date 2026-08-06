package org.jetbrains.plugins.scala.util.assertions

import org.junit.Assert._

import scala.util.matching.Regex

trait StringAssertions {

  def assertStringMatches(string: String, regex: Regex): Unit =
    regex.findAllMatchIn(string).toSeq match {
      case Seq(_) =>
      case _ =>
        fail(
          s"""string doesn't match regular expression:
             |regex: $regex
             |actual: $string""".stripMargin
        )
    }

  def assertStringNotMatches(string: String, regex: Regex): Unit =
    regex.findAllMatchIn(string).toSeq match {
      case Seq() =>
      case matches =>
        fail(
          s"""string should't match regular expression:
             |regex: $regex
             |actual: $string
             |matches:
             |${matches.map(_.matched).mkString("\n")}""".stripMargin
        )
    }

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

  def assertIsBlank(string: String): Unit =
    if (!string.trim.isEmpty)
      fail(s"expected blank string but got:\n${string}")

  private def display(str: String): String =
    str.replace("\\n", "\\\\n")
      .replace("\\r", "\\\\r")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
}

object StringAssertions extends StringAssertions
