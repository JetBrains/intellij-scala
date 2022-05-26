package org.jetbrains.plugins.scala.lang.parser.scala3

class MatchParserTest extends SimpleScala3ParserTestBase {
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

  def testSCL19811(): Unit = checkParseErrors(
    """
      |object A {
      |for {
      |  n <- 1 to 8 if n match {
      |  case x if x > 5 => true
      |  case _ => false
      |}
      |} yield n
      |
      |val x =
      |  123 match {
      |    case 1 => 1
      |    case 123 => 123
      |  } match {
      |    case 1 => 2
      |    case 123 => 321
      |  }
      |}
      |""".stripMargin
  )
}
