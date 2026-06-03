package org.jetbrains.plugins.scala
package lang
package transformation
package general

class ExpandStringInterpolationTest extends TransformerTest(new ExpandStringInterpolation()) {

  def testEmpty(): Unit = check(
    before =
      s"""
      s""
    """,
    after =
      """
      StringContext("").s()
    """
  )()

  def testSingleString(): Unit = check(
    before =
      s"""
      s"foo"
    """,
    after =
      """
      StringContext("foo").s()
    """
  )()

  def testSingleInjection(): Unit = check(
    before =
      s"""
      s"$$A"
    """,
    after =
      """
      StringContext("", "").s(A)
    """
  )()

  def testInterpolation(): Unit = check(
    before =
      s"""
      s"a$${A}b$${B}"
    """,
    after =
      """
      StringContext("a", "b", "").s(A, B)
    """
  )()

  def testJoinedInterpolation(): Unit = check(
    before =
      s"""
      s"$${A}$${B}"
    """,
    after =
      """
      StringContext("", "", "").s(A, B)
    """
  )()

  def testFormat(): Unit = check(
    before =
      s"""
      f"$$A%s"
    """,
    after =
      """
      StringContext("", "%s").f(A)
    """
  )()

  def testFormatInterpolation(): Unit = check(
    before =
      s"""
      f"a$${A}%sb$${B}%d"
    """,
    after =
      """
      StringContext("a", "%sb", "%d").f(A, B)
    """
  )()

  def testJoinedFormatInterpolation(): Unit = check(
    before =
      s"""
      f"$${A}%s$${B}%d"
    """,
    after =
      """
      StringContext("", "%s", "%d").f(A, B)
    """
  )()

  // TODO raw strings
  // TODO $$ escape char
  // TODO pattern strings
  // TODO multiline strings
}
