package org.jetbrains.plugins.scala.lang.exprTree

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.parser.SimpleScalaParserTestBase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers.AssertMatchersExt

class ExprTreeBuilderTest extends SimpleScalaParserTestBase {
  def checkExprTree(code: String, expectedTreeText: String): Unit = {
    val psiTreeFile = parseScalaFile(code, ScalaVersion.Latest.Scala_2_13)
    val expr = psiTreeFile.depthFirst().collectFirst { case expr: ScExpression => expr }.get
    val exprTree = ExprTreeBuilder.build(expr)
    val treeText = ExprTreePrinter.print(exprTree)
    treeText.trim shouldBe expectedTreeText.trim
  }

  def testLiteralInFunc(): Unit = checkExprTree(
    """
      |() => 1
      |""".stripMargin,
    """
      |fun()
      |  •body: 1
      |""".stripMargin
  )

  def testSimpleUnderscore(): Unit = checkExprTree(
    """
      |_
      |""".stripMargin,
    """
      |fun($_0)
      |  •body: $_0
      |""".stripMargin
  )

  def testRef(): Unit = checkExprTree(
    """
      |a.b
      |""".stripMargin,
    """
      |ref:b
      |  •qual: ref:a
      |""".stripMargin
  )

  def testUnderscoreInRef(): Unit = checkExprTree(
    """
      |_.a.b
      |""".stripMargin,
    """
      |fun($_0)
      |  •body: ref:b
      |    •qual: ref:a
      |      •qual: $_0
      |""".stripMargin
  )
}
