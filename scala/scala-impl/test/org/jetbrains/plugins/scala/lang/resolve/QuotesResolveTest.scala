package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class QuotesResolveTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3

  def testQuotesSimple(): Unit =
    checkTextHasNoErrors(
      """
        |import scala.quoted.*
        |object A {
        |  given scala.quoted.Quotes = ???
        |  import quotes.reflect.*
        |}
        |""".stripMargin
    )

  def testSCL20213(): Unit = checkTextHasNoErrors(
    """
      |
      |import scala.quoted.{Expr, Quotes, Type}
      |
      |object Main:
      |  def foo[T: Type](using Quotes): Expr[List[String]] =
      |    import quotes.reflect._
      |    val repr: TypeRepr = TypeRepr.of[T]
      |    val annotations1: List[Term] = repr.typeSymbol.annotations
      |    implicitly[SymbolMethods]
      |    ???
      |""".stripMargin
  )
}
