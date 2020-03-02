package org.jetbrains.plugins.scala.lang.parser

class TypeLambdaParserTest extends SimpleScala3ParserTestBase {
  def testSimple(): Unit = checkParseErrors(
    """
      |trait Monad[F[_]]
      |def tupleMonad[A]: Monad[[B] =>> (A, B)] = ???
      |""".stripMargin
  )

  def testCurried(): Unit = checkParseErrors(
    "type TL = [X] =>> [Y] =>> (X, Y)"
  )

  def testIllegalVariance(): Unit = checkParseErrors(
    """
      |type TL = [X, [[Err(Variance annotation is not allowed here)]]-Y] =>> Map[Y, X]
      |""".stripMargin
  )

  def testBounds(): Unit = checkParseErrors(
    """
      |trait T[F[_]]
      |type TL = [F[_] <: Seq[_]] =>> T[F]
      |""".stripMargin
  )

  def testAllTogether(): Unit = checkParseErrors(
    "type TL = [A, F[A] >: Option[A] <: List[A]] =>> F[A] => A"
  )
}

