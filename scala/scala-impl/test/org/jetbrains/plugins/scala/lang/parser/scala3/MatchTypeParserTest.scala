package org.jetbrains.plugins.scala.lang.parser.scala3

class MatchTypeParserTest extends SimpleScala3ParserTestBase {
  def testSimple(): Unit = checkParseErrors(
    "val a: Int match { case Int => String } = ???"
  )

  def testWhitespace(): Unit = checkParseErrors(
    "type T = Int match { case Int => String case String => Int }" // OK in scalac
  )

  def testSemicolon1(): Unit = checkParseErrors(
    "type T = Int match { case Int => String; }"
  )

  def testSemicolon2(): Unit = checkParseErrors(
    "type T = Int match { case Int => String; case String => Int }"
  )

  def testSemicolon3(): Unit = checkParseErrors(
    "type T = Int match { case Int => String; case String => Int; }"
  )

  def testIndent1(): Unit = checkParseErrors(
    """type T = Int match
      |  case Int => String
      |""".stripMargin
  )

  def testIndent2(): Unit = checkParseErrors(
    """type T = Int match
      |  case Int => String
      |  case String => Int
      |""".stripMargin
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
