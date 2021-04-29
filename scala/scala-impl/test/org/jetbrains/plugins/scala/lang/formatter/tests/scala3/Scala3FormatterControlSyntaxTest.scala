package org.jetbrains.plugins.scala.lang.formatter.tests.scala3

class Scala3FormatterControlSyntaxTest extends Scala3FormatterBaseTest {

  def testIfThenElse(): Unit = doTextTest(
    """class A {
      |  if x < 0 then
      |    "negative"
      |  else if x == 0 then
      |    "zero"
      |  else
      |    "positive"
      |}
      |""".stripMargin
  )

  def testIfThenElse_Unintended(): Unit = doTextTest(
    """class X {
      |if x < 0 then
      |"negative"
      |else if x == 0 then
      |"zero"
      |else
      |"positive"
      |}""".stripMargin,
    """class X {
      |  if x < 0 then
      |    "negative"
      |  else if x == 0 then
      |    "zero"
      |  else
      |    "positive"
      |}
      |""".stripMargin
  )

  def testIfThenElse_WronglyIntended(): Unit = doTextTest(
    """class X {
      |  if x < 0 then
      |    "negative"
      |    else if x == 0 then
      |    "zero"
      |    else
      |    "positive"
      |}
      |""".stripMargin,
    """class X {
      |  if x < 0 then
      |    "negative"
      |  else if x == 0 then
      |    "zero"
      |  else
      |    "positive"
      |}
      |""".stripMargin
  )

  def testIfThenElse_InfixExpr(): Unit = doTextTest(
    """if (x + x).abs > 0
      |then -x
      |else x
      |""".stripMargin
  )

  def testIf_ConditionBlockWithoutBraces(): Unit = doTextTest(
    """if
      |  val x = 1
      |  val y = 2
      |  x + y == 3
      |then
      |  println("Yes1!")
      |else
      |  println("No1 =(")""".stripMargin
  )

  def testIf_ConditionBlockWithBraces(): Unit = doTextTest(
    """if {
      |  val x = 1
      |  val y = 2
      |  x + y == 3
      |}
      |then
      |  println("Yes1!")
      |else
      |  println("No1 =(")""".stripMargin
  )


  def testWhileDo(): Unit = doTextTestWithExtraSpaces(
    """while x >= 0 do x = f(x)
      |
      |while x >= 0
      |do x = f(x)
      |
      |while x >= 0 do
      |  x = f(x)
      |
      |while x >= 0
      |do
      |  x = f(x)
      |
      |while
      |  x >= 0
      |do
      |  x = f(x)
      |
      |while
      |  x < 100 &&
      |    x > 20
      |do
      |  x = x + 1
      |  x = x * 2
      |  println(x)
      |""".stripMargin
  )

  def testWhile_ConditionIsBlockWithoutBraces(): Unit = doTextTest(
    """var idx = 2
      |while
      |  println("in while condition")
      |  idx -= 1
      |  idx >= 0
      |do
      |  println("in while body")
      |""".stripMargin
  )

  def testWhile_ConditionIsBlockWithBraces(): Unit = doTextTest(
    """var idx = 2
      |while {
      |  println("in while condition")
      |  idx -= 1
      |  idx >= 0
      |} do
      |  println("in while body")
      |""".stripMargin
  )

  def testFor_OneLineFor_YieldOnSameLine(): Unit = doForYieldDoTest(
    """for x <- 0 to 2 yield x * 2
      |
      |for x <- 0 to 2 if x > 1 yield x * 2
      |
      |for x <- 0 to 2; y <- 0 to 2 yield x * 2
      |
      |for x <- 0 to 2; y <- 0 to 2 if x > 1 yield x * 2
      |
      |for x <- 0 to 2 if x > 1 yield
      |  x * 2
      |""".stripMargin
  )

  private val OneLineFor =
    """for x <- 0 to 2
      |yield x * 2
      |
      |for x <- 0 to 2 if x > 1
      |yield x * 2
      |
      |for x <- 0 to 2; y <- 0 to 2
      |yield x * 2
      |
      |for x <- 0 to 2; y <- 0 to 2 if x > 1
      |yield x * 2
      |
      |for (x <- 0 to 2; y <- 0 to 2)
      |yield x * 2
      |
      |for {x <- 0 to 2; y <- 0 to 2}
      |yield x * 2
      |""".stripMargin
  private val OneLineForIndented =
    """for x <- 0 to 2
      |  yield x * 2
      |
      |for x <- 0 to 2 if x > 1
      |  yield x * 2
      |
      |for x <- 0 to 2; y <- 0 to 2
      |  yield x * 2
      |
      |for x <- 0 to 2; y <- 0 to 2 if x > 1
      |  yield x * 2
      |
      |for (x <- 0 to 2; y <- 0 to 2)
      |  yield x * 2
      |
      |for {x <- 0 to 2; y <- 0 to 2}
      |  yield x * 2
      |""".stripMargin
  def testFor_OneLineEnumerators(): Unit = {
    scalaSettings.INDENT_YIELD_AFTER_ONE_LINE_ENUMERATORS = false
    doForYieldDoTest(OneLineFor)
  }

  def testFor_OneLineEnumerators_IndentYieldInOneLineFor(): Unit = {
    scalaSettings.INDENT_YIELD_AFTER_ONE_LINE_ENUMERATORS = true
    doForYieldDoTest(OneLineFor, OneLineForIndented)
  }

  private val MultilineFor =
    """for
      |  x <- 0 to 2
      |yield x * 2
      |
      |for {
      |  x <- 0 to 2
      |}
      |yield x * 2
      |
      |for
      |  x <- 0 to 2
      |yield
      |  x * 2
      |
      |for
      |  x <- 0 to 2
      |  y <- 0 to 2
      |  z <- 0 to 2
      |yield
      |  x * 2
      |
      |for
      |  x <- 0 to 2
      |  if x > 1
      |  y <- 0 to 2
      |  if y > 1
      |  if x + y > 1
      |yield x * x
      |
      |for
      |  x <- 0 to 2
      |  y <- 0 to 2
      |yield
      |  val y = x
      |  println()
      |  y + 1
      |
      |""".stripMargin

  def testFor_MultilineEnumerators(): Unit = {
    scalaSettings.INDENT_YIELD_AFTER_ONE_LINE_ENUMERATORS = false
    doForYieldDoTest(MultilineFor)

    // the setting shouldn't affect anything in multiline for
    scalaSettings.INDENT_YIELD_AFTER_ONE_LINE_ENUMERATORS = true
    doForYieldDoTest(MultilineFor)
  }

  def testFor_WithAssigmentToValue(): Unit = doForYieldDoTest(
    """class A {
      |  val x =
      |    for x <- 0 to 2
      |      yield x * 2
      |
      |  val y =
      |    for
      |      x <- 0 to 2
      |      y <- 0 to 2
      |    yield
      |      val y = x
      |      println()
      |      y + 1
      |}
      |""".stripMargin
  )

  // http://dotty.epfl.ch/docs/reference/other-new-features/control-syntax.html
  // A catch can be followed by a single case on the same line.
  // If there are multiple cases, these have to appear within braces (just like in Scala 2) or an INTENDED BLOCK.
  def testTryCatch_1_SingleCaseClause(): Unit = doTextTestWithExtraSpaces(
    """try 42 catch case ex: Exception => println(42)
      |
      |try 42
      |catch case ex: Exception => println(42)
      |
      |try 42
      |catch
      |  case ex: Exception => println(42)
      |""".stripMargin
  )

  def testTryCatch_2(): Unit = doTextTestWithExtraSpaces(
    """try throw new Exception("test") catch
      |  case ex: Exception => println(1)
      |  case ex: Error => println(2)
      |""".stripMargin
  )

  def testTryCatch_3(): Unit = doTextTest(
    """try throw new Exception("test") catch
      |  case ex: Error =>
      |    println(s"caught error: ${ex.getMessage}")
      |  case ex: Exception =>
      |    println(s"caught exception: ${ex.getMessage}")
      |""".stripMargin
  )

  def testTryCatch_4(): Unit = doTextTestWithExtraSpaces(
    """try throw new Exception("test")
      |catch
      |  case ex: Exception => println(1)
      |  case ex: Error => println(2)
      |""".stripMargin
  )

  def testTryCatch_5(): Unit = doTextTest(
    """try throw new Exception("test")
      |catch
      |  case ex: Error =>
      |    println(s"caught error: ${ex.getMessage}")
      |  case ex: Exception =>
      |    println(s"caught exception: ${ex.getMessage}")
      |""".stripMargin
  )

  def testTryCatch_6(): Unit = doTextTestWithExtraSpaces(
    """try
      |  var x = 42
      |  var y = x + 1
      |  println(x + y)
      |catch
      |  case ex: Exception =>
      |    println(1)
      |    println(2)
      |  case ex: Error =>
      |    println(3)
      |    println(4)
      |""".stripMargin
  )

  def testTry_SingleExpressionOnNewLine(): Unit = doTextTestWithExtraSpaces(
    """try
      |  throw new Exception("test")
      |catch {
      |  case _ =>
      |}
      |""".stripMargin
  )

  def testTryCatch_IndentedCaseClause(): Unit = doTextTestWithExtraSpaces(
    """try println(42)
      |catch
      |  case ex: ClassCastException => ex.printStackTrace()
      |    case ex: IndexOutOfBoundsException => ex.printStackTrace()
      |      case ex: Exception => ex.printStackTrace()
      |""".stripMargin,
    """try println(42)
      |catch
      |  case ex: ClassCastException => ex.printStackTrace()
      |  case ex: IndexOutOfBoundsException => ex.printStackTrace()
      |  case ex: Exception => ex.printStackTrace()
      |""".stripMargin
  )

  def testMatch(): Unit = doTextTest(
    """Option(42) match
      |  case Some(42) => println(1)
      |    case _ => println(2)
      |""".stripMargin,
    """Option(42) match
      |  case Some(42) => println(1)
      |  case _ => println(2)
      |""".stripMargin,
    repeats = 3
  )

  def testMatch_InsideFunctionWithBraces(): Unit = doTextTest(
    """def foo(): Unit = {
      |  Option(42) match
      |      case Some(42) => println(1)
      |         case _ => println(2)
      |}""".stripMargin,
    """def foo(): Unit = {
      |  Option(42) match
      |    case Some(42) => println(1)
      |    case _ => println(2)
      |}""".stripMargin,
    repeats = 3
  )

  def testMatch_IndentedCase(): Unit = doTextTest(
    """1 match
      |  case 1 => 11
      |    case 2 => 22
      |""".stripMargin,
    """1 match
      |  case 1 => 11
      |  case 2 => 22
      |""".stripMargin,
  )

  def testMatch_IndentedIncompleteCase(): Unit = doTextTest(
    """1 match
      |  case 1 => 11
      |    case
      |""".stripMargin,
    """1 match
      |  case 1 => 11
      |  case
      |""".stripMargin,
  )

  def testMatch_IndentedIncompleteCase_1(): Unit = doTextTest(
    """1 match
      |  case 1 => 11
      |    case 2
      |""".stripMargin,
    """1 match
      |  case 1 => 11
      |  case 2
      |""".stripMargin,
  )

  def testMatch_Nested(): Unit = doTextTest(
    """3 match
      |  case 1 => 11
      |  case 2 =>
      |    "a" match
      |      case "a" => "aa"
      |      case "b" => "bb"
      |  case 3 => 33
      |""".stripMargin
  )

  def testMatch_Nested_IndentedCase(): Unit = doTextTest(
    """3 match
      |  case 1 => 11
      |  case 2 =>
      |    "a" match
      |      case "a" => "aa"
      |        case "b" => "bb"
      |  case 3 => 33
      |""".stripMargin,
    """3 match
      |  case 1 => 11
      |  case 2 =>
      |    "a" match
      |      case "a" => "aa"
      |      case "b" => "bb"
      |  case 3 => 33
      |""".stripMargin
  )

  def testMatch_Nested_IndentedIncompleteCase(): Unit = doTextTest(
    """3 match
      |  case 1 => 11
      |  case 2 =>
      |    "a" match
      |      case "a" => "aa"
      |        case
      |  case 3 => 33
      |""".stripMargin,
    """3 match
      |  case 1 => 11
      |  case 2 =>
      |    "a" match
      |      case "a" => "aa"
      |      case
      |  case 3 => 33
      |""".stripMargin
  )

  def testMatch_CasesOnSameLine(): Unit = {
    doTextTest(
      """2 match
        |  case 1 => 11 case 2 => 22
        |""".stripMargin,
      """2 match
        |  case 1 => 11
        |  case 2 => 22
        |""".stripMargin
    )
  }
}
