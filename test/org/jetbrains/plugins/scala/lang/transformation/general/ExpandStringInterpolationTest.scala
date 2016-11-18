package org.jetbrains.plugins.scala.lang.transformation.general

import org.jetbrains.plugins.scala.lang.transformation.TransformerTest

/**
  * @author Pavel Fatin
  */
class ExpandStringInterpolationTest extends TransformerTest(new ExpandStringInterpolation()) {
  def testEmpty() = check(
    s"""
      s""
    """,
    """
      StringContext("").s()
    """
  )

  def testSingleString() = check(
    s"""
      s"foo"
    """,
    """
      StringContext("foo").s()
    """
  )

  def testSingleInjection() = check(
    s"""
      s"$$A"
    """,
    """
      StringContext("", "").s(A)
    """
  )

  def testInterpolation() = check(
    s"""
      s"a$${A}b$${B}"
    """,
    """
      StringContext("a", "b", "").s(A, B)
    """
  )

  def testJoinedInterpolation() = check(
    s"""
      s"$${A}$${B}"
    """,
    """
      StringContext("", "", "").s(A, B)
    """
  )

  def testFormat() = check(
    s"""
      f"$$A%s"
    """,
    """
      StringContext("", "%s").f(A)
    """
  )

  def testFormatInterpolation() = check(
    s"""
      f"a$${A}%sb$${B}%d"
    """,
    """
      StringContext("a", "%sb", "%d").f(A, B)
    """
  )

  def testJoinedFormatInterpolation() = check(
    s"""
      f"$${A}%s$${B}%d"
    """,
    """
      StringContext("", "%s", "%d").f(A, B)
    """
  )

  // TODO raw strings
  // TODO $$ escape char
  // TODO pattern strings
  // TODO multiline strings
}
