package org.jetbrains.plugins.scala.lang.formatter.tests.scala3

import org.jetbrains.plugins.scala.extensions.StringExt

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

  private def doTextTestWithExtraSpaces(before0: String): Unit =
    doTextTestWithExtraSpaces(before0, before0)

  private def doTextTestWithExtraSpaces(before0: String, after0: String): Unit = {
    val before = before0.withNormalizedSeparator
    val after = after0.withNormalizedSeparator
    doTextTest(before, after)
    val textWithExtraSpaces = before.replaceAll("\\s", "$0 ") // insert space after any other space/new line
    doTextTest(textWithExtraSpaces, after)
  }

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
  private def doForYieldDoTest(before: String): Unit =
    doForYieldDoTest(before, before)

  private def doForYieldDoTest(before: String, after: String): Unit ={
    doTextTestWithExtraSpaces(before, after)

    val beforeWithDo = before.replace("yield", "do")
    val afterWithDo = after.replace("yield", "do")
    doTextTestWithExtraSpaces(beforeWithDo, afterWithDo)
  }

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
}
