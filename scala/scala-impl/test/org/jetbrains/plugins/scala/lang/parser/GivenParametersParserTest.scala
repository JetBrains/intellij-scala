package org.jetbrains.plugins.scala.lang.parser

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.util.PsiSelectionUtil

class GivenParametersParserTest extends SimpleParserTestBase with PsiSelectionUtil {
  import junit.framework.Assert._

  def test_simple(): Unit = {
    val file = checkParseErrors(
      """
        |def test(a: Int) given (x: Int) = ()
        |""".stripMargin
    )

    val fun = selectElement[ScFunction](file, path("O", "test"))
    val paramClause = fun.paramClauses.clauses
    assertEquals(2, paramClause.size)
    assert(!paramClause.head.isGiven)
    assert(paramClause.last.isGiven)
  }

  def test_newline(): Unit = checkParseErrors(
    """
      |
      |def test(a: Int)
      |  given
      |
      |
      |
      |
      |
      |
      |  (x: Int) = ()
      |""".stripMargin
  )

  /*
    TODO: to make this work, given must be a real keyword
  def test_newline_2(): Unit = checkParseErrors(
    s"""
      |
      |def test(a: Int)
      |
      | ${err("here should be an error")} given (x: Int) = ()
      |""".stripMargin
  )

   */

  def test_property(): Unit = checkParseErrors(
    "def test given (x: Int) = ()"
  )

  def test_given_types(): Unit = checkParseErrors(
    "def test given Int, String = ()"
  )

  def test_multiple_clauses(): Unit = checkParseErrors(
    "def test given (i: Int) given Int: Int = 3"
  )

  val expectedGiven = ScalaBundle.message("given.keyword.expected")

  def test_missing_given(): Unit = checkParseErrors(
  s"def test given (i: Int)${err(expectedGiven)}(i2: String): Unit = ()"
  )

  def test_instruction_after_given_func(): Unit = checkParseErrors(
    """
      |def test given (i: Int) given Int given String = {
      |  i
      |}
      |
      |test()
      |""".stripMargin
  )
}
