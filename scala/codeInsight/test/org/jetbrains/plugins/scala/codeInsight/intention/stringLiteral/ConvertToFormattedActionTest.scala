package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

import org.jetbrains.plugins.scala.codeInsight.intentions

class ConvertToFormattedActionTest extends intentions.ScalaIntentionTestBase {

  override def familyName: String = FormatConversionIntention.ConvertToFormatted

  private val data: Seq[(String, String)] = Seq(
    // %% escape
    """"%" + 1"""                -> """"%%1".format()""",
    """"% " + 1"""               -> """"%% 1".format()""",
    """"%%" + 1"""               -> """"%%%%1".format()""",
    """"%%%" + 1"""              -> """"%%%%%%1".format()""",
    """" %% " + 1"""             -> """" %%%% 1".format()""",
    """" %%% " + 1"""            -> """" %%%%%% 1".format()""",
    """"% %" + 1"""              -> """"%% %%1".format()""",
    """" % % " + 1"""            -> """" %% %% 1".format()""",
    """"a % b %% c %%%" + 1"""   -> """"a %% b %%%% c %%%%%%1".format()""",
    """" a % b %% c %%% " + 1""" -> """" a %% b %%%% c %%%%%% 1".format()""",
    """"% %d %% %n %n" + 1"""    -> """"%% %%d %%%% %%n %%n1".format()""",
    """"% %d %% %n %n " + 1"""    -> """"%% %%d %%%% %%n %%n 1".format()""",
    // %n (which is not an escape in non formatted string)
    """"%n" + 1"""              -> """"%%n1".format()""",
    """"%n %%n" + 1"""          -> """"%%n %%%%n1".format()""",
    """" %n %%n " + 1"""        -> """" %%n %%%%n 1".format()""",
  ).map { case (in, res) =>
    ((in.replaceFirst(raw"\+", "<caret>+")), res) // place caret at concatenation
  }

  private def toMultiline(s: String): String = s.replace("\"", "\"\"\"")

  private val dataMultiline = data.map { case (input, result) => (toMultiline(input), result) }
  private val dataInterpolated = data.map { case (input, result) => ("s" + input, result) }
  private val dataInterpolatedMultiline = dataInterpolated.map { case (input, result) => (toMultiline(input), result) }


  private val myDoTest: Tuple2[String, String] => Unit = { case (input, result) =>
    doTest(input, result)
  }

  def testEscapePercent_FromPlainString(): Unit = data.foreach(myDoTest)
  def testEscapePercent_FromMultilineString(): Unit = dataMultiline.foreach(myDoTest)
  def testEscapePercent_FromInterpolatedString(): Unit = dataInterpolated.foreach(myDoTest)
  def testEscapePercent_FromInterpolatedMultilineString(): Unit = dataInterpolatedMultiline.foreach(myDoTest)

  def testEscapePercent_FromInterpolatedString_1(): Unit =
    doTest(
      """s"% %% | ${"%"} ${"%"}% | ${"%%"} ${"%%%"} | ${"%%"}% ${"%%%"}% ${"a%b%%c%%%"}"""",
      """"%% %%%% | %% %%%% | %%%% %%%%%% | %%%%%% %%%%%%%% a%%b%%%%c%%%%%%".format() """
    )

  def testFromFormatInterpolator(): Unit =
    doTest(
      """val height = 1.9d
        |val name = "James"
        |<caret>f"$name%s is $height%2.2f meters tall"
        |""".stripMargin,
      """val height = 1.9d
        |val name = "James"
        |"%s is %2.2f meters tall".format(name, height)""".stripMargin
    )

  def testPreservePercentEscape_FromFormatInterpolator(): Unit =
    doTest(
      """f"%% %%%% %% %n %%n %%%n"""",
      """"%% %%%% %% %n %%n %%%n".format()""",
    )

  def testPreserveSpecifiersAndExpressionText_FromFormatInterpolator(): Unit =
    doTest(
      """f"${1}%d ${"value1"} ${"value2"}%s ${2f}%f ${2.0}%f"""",
      """"%d value1 %s %f %f".format(1, "value2", 2f, 2.0)""",
    )

  def testHandlePercentEscapeAtSpecifierPosition_FromFormatInterpolator(): Unit =
    doTest(
      """case class A(x: String)
        |
        |val a = A("qwe")
        |val x = 42
        |<caret>f"${2}%% ${x}%% ${A("hello")}%% ${a}%% ${"value"}%%"""".stripMargin,
      """case class A(x: String)
        |
        |val a = A("qwe")
        |val x = 42
        |"2%% %d%% %s%% %s%% value%%".format(x, A("hello"), a)""".stripMargin,
    )

  def testHandlePercentNewLineEscapeAtSpecifierPosition_FromFormatInterpolator(): Unit =
    doTest(
      // in this input: ${...}%n
      // %n is not a specifier but a special escape which denotes a new line
      """case class A(x: String)
        |
        |val a = A("qwe")
        |val x = 42
        |<caret>f"%n %%n ${2}%n ${x}%n ${A("hello")}%n ${a}%n ${"value"}%n"""".stripMargin,
      """case class A(x: String)
        |
        |val a = A("qwe")
        |val x = 42
        |"%n %%n 2%n %d%n %s%n %s%n value%n".format(x, A("hello"), a)""".stripMargin,
    )
}
