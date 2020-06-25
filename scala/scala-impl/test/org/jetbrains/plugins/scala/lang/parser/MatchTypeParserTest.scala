package org.jetbrains.plugins.scala.lang.parser

class MatchTypeParserTest extends SimpleScala3ParserTestBase {
  def testSimple(): Unit = checkParseErrors(
    "val a: Int match { case Int => String } = ???"
  )

  def testAliasDef(): Unit = checkParseErrors(
    """
      |type Elem[X] = X match {
      |  case String      => Char
      |  case Array[t]    => t
      |  case Iterable[t] => t
      |  case AnyVal      => X
      |}
      |""".stripMargin
  )

  def testAliasWithBound(): Unit = checkParseErrors(
    """
      |type Concat[+Xs <: Tuple, +Ys <: Tuple] <: Tuple = Xs match {
      |  case Unit    => Ys
      |  case x *: xs => x *: Concat[xs, Ys]
      |}
      |""".stripMargin
  )
}
