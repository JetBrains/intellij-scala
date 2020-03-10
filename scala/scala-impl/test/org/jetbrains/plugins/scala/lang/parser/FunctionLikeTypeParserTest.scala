package org.jetbrains.plugins.scala.lang.parser

class FunctionLikeTypeParserTest extends SimpleScala3ParserTestBase {
  def testContextualFunction(): Unit =
    checkParseErrors(
      """
        |type Executable[T] = ExecutionContext ?=> T
        |def table(init: Table ?=> Unit) = ???
        |""".stripMargin
    )

  def testPolyFunction(): Unit =
    checkParseErrors(
      """
        |def foo(fn: [T <: AnyVal] => List[T] => List[(T, T)]): Unit
        |type Eq1[F[_]] = [A] => Eq[A] ?=> Eq[F[A]]
        |""".stripMargin
    )

  def testDependentFunctionType(): Unit =
    checkParseErrors(
      """
        |val extractor: (e: Entry) => e.Key = extractKey
        |def foo(fn: (b: Bar) => b.TypeMember): Unit
        |""".stripMargin
    )

  def testContextualFunctionExplicitModifier(): Unit =
    checkParseErrors(
      """
        |type Executable[T] = (given ExecutionContext) => T
        |def table(init: (given Table) => Unit) = ???
        |""".stripMargin
    )
}
