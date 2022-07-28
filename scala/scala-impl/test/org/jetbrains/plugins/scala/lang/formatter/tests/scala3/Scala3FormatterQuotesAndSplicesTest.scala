package org.jetbrains.plugins.scala.lang.formatter.tests.scala3

//Note: test data might not contain self-contained compiled code, but still the code should be enough to compile
class Scala3FormatterQuotesAndSplicesTest extends Scala3FormatterBaseTest {

  def testSplicesEmpty(): Unit = doTextTest(
    """'{}
      |'{     }
      |'{
      |
      |}""".stripMargin,
    """'{}
      |'{}
      |'{}""".stripMargin
  )

  def testSplicesWithDummyExpression(): Unit = doTextTest(
    """'{???}
      |'{ ??? }
      |'{  ???  }""".stripMargin,
    """'{ ??? }
      |'{ ??? }
      |'{ ??? }""".stripMargin
  )

  def testSplicesAndQuotes(): Unit = doTextTest(
    """'{42 + 23}
      |'{foo()}
      |'{foo(${42 + 23})}
      |
      |'{   42  +  23   }
      |'{   foo()   }
      |'{   foo(${   42 + 23   })   }
      |""".stripMargin,
    """'{ 42 + 23 }
      |'{ foo() }
      |'{ foo(${ 42 + 23 }) }
      |
      |'{ 42 + 23 }
      |'{ foo() }
      |'{ foo(${ 42 + 23 }) }
      |""".stripMargin
  )

  def testQuotedPattern(): Unit = doTextTest(
    """package macro_examples
      |
      |import scala.quoted.*
      |
      |def oneLevel1(expr: Expr[Any])(using Quotes): Expr[Int] = expr match
      |  case '{ $_ } => ???
      |  case '{ foo($_) } => ???
      |  case '{ foo($y) } => ???
      |
      |  case '{ foo(${ _ }) } => ???
      |  case '{ foo(${ y }) } => ???
      |
      |  case '{ foo(${ MyMatcher(_) }) } => ???
      |  case '{ foo(${ MyMatcher(y) }) } => ???
      |
      |  //without @
      |  case '{ foo(${ MyMatcher(_) }) } => ???
      |  case '{ foo(${ MyMatcher(_*) }) } => ???
      |  case '{ foo(${ MyMatcher(MyMatcher2()) }) } => ???
      |
      |  //with @
      |  case '{ foo(${ MyMatcher(y@_) }) } => ???
      |  case '{ foo(${ MyMatcher(y@_*) }) } => ???
      |  case '{ foo(${ MyMatcher(y@MyMatcher2()) }) } => ???
      |
      |  //new expression
      |  case '{ foo(new AnyRef()) } => ???
      |""".stripMargin
  )

  //NOTE: testing that at least parsing doesn't break code too much
  //I am not 100% sure what the expected result should be, so feel free to change
  def testQuotedPatternBad(): Unit = doTextTest(
    """package macro_examples
      |
      |import scala.quoted.*
      |
      |def oneLevel1(expr: Expr[Any])(using Quotes): Expr[Int] = expr match
      |  case '{ foo(${_*ab}) } => ???
      |""".stripMargin,
    """package macro_examples
      |
      |import scala.quoted.*
      |
      |def oneLevel1(expr: Expr[Any])(using Quotes): Expr[Int] = expr match
      |  case '{ foo(${ _ * ab }) } => ???
      |""".stripMargin
  )


  //https://dotty.epfl.ch/docs/reference/metaprogramming/macros.html#quoted-patterns
  def testQuotedPattern_ExampleFromDocumentation1(): Unit = doTextTest(
    """def sum(args: Int*): Int = args.sum
      |
      |inline def optimize(inline arg: Int): Int = ${ optimizeExpr('arg) }
      |
      |private def optimizeExpr(body: Expr[Int])(using Quotes): Expr[Int] =
      |  body match
      |    // Match a call to sum without any arguments
      |    case '{ sum() } => Expr(0)
      |    // Match a call to sum with an argument $n of type Int.
      |    // n will be the Expr[Int] representing the argument.
      |    case '{ sum($n) } => n
      |    // Match a call to sum and extracts all its args in an `Expr[Seq[Int]]`
      |    case '{ sum(${ Varargs(args) }: _*) } => sumExpr(args)
      |    case body => body
      |
      |private def sumExpr(args1: Seq[Expr[Int]])(using Quotes): Expr[Int] =
      |  def flatSumArgs(arg: Expr[Int]): Seq[Expr[Int]] = arg match
      |    case '{ sum(${ Varargs(subArgs) }: _*) } => subArgs.flatMap(flatSumArgs)
      |    case arg => Seq(arg)
      |
      |  val args2 = args1.flatMap(flatSumArgs)
      |  val staticSum: Int = args2.map(_.value.getOrElse(0)).sum
      |  val dynamicSum: Seq[Expr[Int]] = args2.filter(_.value.isEmpty)
      |  dynamicSum.foldLeft(Expr(staticSum))((acc, arg) => '{ $acc + $arg })
      |
      |optimize {
      |  sum(sum(1, a, 2), 3, b)
      |} // should be optimized to 6 + a + b
      |""".stripMargin
  )

  //https://dotty.epfl.ch/docs/reference/metaprogramming/macros.html#recovering-precise-types-using-patterns
  def testQuotedPattern_ExampleFromDocumentation2(): Unit = doTextTest(
    """def f(expr: Expr[Any])(using Quotes) = expr match
      |  case '{ $x: t } =>
      |
      |extension (inline sc: StringContext)
      |  inline def showMe(inline args: Any*): String = ${ showMeExpr('sc, 'args) }
      |
      |private def showMeExpr(sc: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using Quotes): Expr[String] =
      |  import quotes.reflect.report
      |  argsExpr match
      |    case Varargs(argExprs) =>
      |      val argShowedExprs = argExprs.map {
      |        case '{ $arg: tp } =>
      |          Expr.summon[Show[tp]] match
      |            case Some(showExpr) =>
      |              '{ $showExpr.show($arg) }
      |            case None =>
      |              report.error(s"could not find implicit for ${Type.show[Show[tp]]}", arg); '{ ??? }
      |      }
      |      val newArgsExpr = Varargs(argShowedExprs)
      |      '{ $sc.s($newArgsExpr: _*) }
      |    case _ =>
      |      // `new StringContext(...).showMeExpr(args: _*)` not an explicit `showMeExpr"..."`
      |      report.error(s"Args must be explicit", argsExpr)
      |      '{ ??? }
      |
      |trait Show[-T]:
      |  def show(x: T): String
      |
      |// in a different file
      |given Show[Boolean] with
      |  def show(b: Boolean) = "boolean!"
      |
      |println(showMe"${true}")""".stripMargin
  )

  //https://dotty.epfl.ch/docs/reference/metaprogramming/macros.html#open-code-patterns
  def testQuotedPattern_ExampleFromDocumentation3(): Unit = doTextTest(
    """inline def eval(inline e: Int): Int = ${ evalExpr('e) }
      |
      |private def evalExpr(e: Expr[Int])(using Quotes): Expr[Int] = e match
      |  case '{ val y: Int = $x ; $body(y): Int } =>
      |    // body: Expr[Int => Int] where the argument represents
      |    // references to y
      |    evalExpr(Expr.betaReduce('{ $body(${ evalExpr(x) }) }))
      |  case '{ ($x: Int) * ($y: Int) } =>
      |    (x.value, y.value) match
      |      case (Some(a), Some(b)) => Expr(a * b)
      |      case _ => e
      |  case _ => e
      |
      |eval { // expands to the code: (16: Int)
      |  val x: Int = 4
      |  x * x
      |}
      |""".stripMargin
  )

}
