package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.util.PsiSelectionUtil

class EndParserTest extends SimpleScala3ParserTestBase with PsiSelectionUtil with AssertionMatchers {
  def doTest(code: String, expectedType: IElementType): Unit = {
    val file = checkParseErrors(code.stripMargin)

    val endElement = searchElement[ScEnd](file)

    val designator = endElement.endingElementDesignator
    designator shouldNotBe null

    val designatorType = endElement.endingElementDesignator.getNode.getElementType
    designatorType shouldBe expectedType
  }

  def test_end_if(): Unit = doTest(
    """
      |if (boolean)
      |  stmt1
      |  stmt2
      |else
      |  stmt3
      |  stmt4
      |end if
      |""".stripMargin,
    expectedType =  ScalaTokenTypes.kIF
  )

  /*def test_end_while(): Unit = doTest(
    """
      |while
      |  stmt1
      |  stmt2
      |do
      |  stmt3
      |  stmt4
      |end while
      |""".stripMargin,
    expectedType =  ScalaTokenTypes.kWHILE
  )

  def test_end_for(): Unit = doTest(
    """
      |for
      |  x <- xs
      |do
      |  stmt1
      |end for
      |""".stripMargin,
    expectedType = ScalaTokenTypes.kFOR
  )*/

  def test_end_try_finally(): Unit = doTest(
    """
      |try
      |  stmt1
      |  stmt2
      |finally
      |  stmt3
      |  stmt4
      |end try
      |""".stripMargin,
    expectedType =  ScalaTokenTypes.kTRY
  )

  def test_end_try_catch(): Unit = doTest(
    """
      |try
      |  stmt1
      |  stmt2
      |catch
      |case a => stmt3
      |case b => stmt4
      |end try
      |""".stripMargin,
    expectedType =  ScalaTokenTypes.kTRY
  )

  def test_end_match(): Unit = doTest(
    """
      |something match
      |case a =>  stmt1
      |case _ => stmt2
      |end match
      |""".stripMargin,
    expectedType =  ScalaTokenTypes.kMATCH
  )

  def test_end_new(): Unit = doTest(
    """
      |new:
      |  stmt1
      |  stmt2
      |end new
      |""".stripMargin,
    expectedType =  ScalaTokenType.NewKeyword
  )

  def test_end_class(): Unit = doTest(
    """
      |class A:
      |  stmt1
      |  stmt2
      |end A
      |""".stripMargin,
    expectedType =  ScalaTokenTypes.tIDENTIFIER
  )
  def test_end_method(): Unit = doTest(
    """
      |def test() =
      |  stmt1
      |  stmt2
      |end test
      |""".stripMargin,
    expectedType =  ScalaTokenTypes.tIDENTIFIER
  )

  // todo: add tests for extensions and given
}
