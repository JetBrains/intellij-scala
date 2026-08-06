package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.junit.Assert.assertEquals

class ScEffectiveSignatureClausesTest extends ScalaFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  private def doFunctionTest(text: String, expected: Seq[String]): Unit = {
    val file = myFixture.configureByText(s"${getTestName(false)}.scala", text)
    val function = file
      .depthFirst()
      .filterByType[ScFunction]
      .find(_.name == "foo")
      .getOrElse(throw new AssertionError("Expected to find function foo"))

    assertEquals(expected, describe(function.effectiveSignatureClauses))
  }

  private def describe(clauses: Seq[ScSignatureClause]): Seq[String] =
    clauses.map {
      case ScSignatureClause.TypeClause(clause) =>
        s"type(${clause.typeParameters.map(_.name).mkString(", ")})"
      case ScSignatureClause.TermClause(clause) =>
        s"term(${describeParameters(clause.effectiveParameters)})"
    }

  private def describeParameters(parameters: Seq[params.ScParameter]): String =
    parameters.map { parameter =>
      ScalaPsiUtil
        .findSyntheticContextBoundInfo(parameter)
        .map(info => s"contextBound(${info.typeParam.name})")
        .getOrElse(parameter.name)
    }.mkString(", ")

  def testContextBoundClausesPreserveInterleavedTypeClauses(): Unit = doFunctionTest(
    """trait TC[A]
      |
      |def foo[A: TC](a: A)[B: TC](b: B): Unit = ()
      |""".stripMargin,
    Seq(
      "type(A)",
      "term(a)",
      "type(B)",
      "term(b)",
      "term(contextBound(A), contextBound(B))"
    )
  )

  def testContextBoundsArePrependedToExistingUsingClause(): Unit = doFunctionTest(
    """trait TC[A]
      |
      |def foo[A: TC](a: A)[B: TC](using b: B): Unit = ()
      |""".stripMargin,
    Seq(
      "type(A)",
      "term(a)",
      "type(B)",
      "term(contextBound(A), contextBound(B), b)"
    )
  )
}
