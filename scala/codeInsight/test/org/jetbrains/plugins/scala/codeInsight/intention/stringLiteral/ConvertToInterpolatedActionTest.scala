package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

class ConvertToInterpolatedActionTest extends StringConversionTestBase {

  override def familyName: String = FormatConversionIntention.ConvertToInterpolated

  def test_SCL_5275(): Unit =
    doTest(
      """val a = <caret>"http://%s:%d/more".format(addr, port)""".stripMargin,
      """val a = s"http://$addr:$port/more"""".stripMargin,
    )

  def test_SCL_5275_1(): Unit =
    doTest(
      """<caret>"%s / %s".format(1, 2)""".stripMargin,
      """"1 / 2"""".stripMargin,
    )

  def test_SCL_5275_2(): Unit =
    doTest(
      """<caret>"%s / %s / %s".format(1, 2, x)""".stripMargin,
      """s"1 / 2 / $x"""".stripMargin,
    )

  def test_SCL_5386(): Unit =
    doTest(
      """val x = 1
        |<caret>"%sfoo".format(x)""".stripMargin,
      """val x = 1
        |s"${x}foo"""".stripMargin,
    )

  def testFromConcatenation_WithPercents(): Unit =
    doTest(
      """"" + 42 + "%" + "%%" + "%%%" + "a%" + "a%%" + "a%%%" + "%b" + "%%b" + "%%%b"""",
      """"42%%%%%%a%a%%a%%%%b%%b%%%b""""
    )

  def testFromConcatenation_WithFormattedWithPercents(): Unit =
    doTest(
      """"" + 42 + "%%".format() + "a%%".format() + "%%b".format()""",
      """"42%a%%b""""
    )

  //SCL-18617
  def testFromConcatenation_WithPlainString(): Unit =
    doTest(
      s"""val a = <caret>"\\\\ \\u0023 \\\\u0023" + str""",
      s"""val a = <caret>s"\\\\ # \\\\u0023$$str""""
    )

  def testFromConcatenation_WithMultilineString(): Unit =
    doTest(
      s"""val a = <caret>\"\"\"\\\\ \\u0023 \\\\u0023\"\"\" + str""",
      s"""val a = <caret>s"\\\\\\\\ # \\\\\\\\u0023$$str""""
    )

  def testFromConcatenation_WithMultilineString_WithMargins_WithEscapeUnicode(): Unit =
    doTest(
      s"""val str = "text"
         |<caret>'''prefix \\\\u0023 \\\\\\u0023 \\\\\\\\u0023
         |  |\\\\u0023 \\\\\\u0023 \\\\\\\\u0023 suffix
         |  |\\\\u0023 \\\\\\u0023 \\\\\\\\u0023
         |  |'''.stripMargin + str
         |""".stripMargin.fixTripleQuotes,
      s"""val str = "text"
         |<caret>s'''prefix \\\\\\\\u0023 \\\\\\\\# \\\\\\\\\\\\\\\\u0023
         |   |\\\\\\\\u0023 \\\\\\\\# \\\\\\\\\\\\\\\\u0023 suffix
         |   |\\\\\\\\u0023 \\\\\\\\# \\\\\\\\\\\\\\\\u0023
         |   |$$str'''.stripMargin
         |""".stripMargin.fixTripleQuotes
    )

  def testFromConcatenation_WithMultilineString_WithMargins_WithEscapeChars(): Unit =
    doTest(
      s"""val str = "text"
         |<caret>'''prefix \\ \\\\ \\\\\\\\ \\b \\t
         |  |\\ \\\\ \\\\\\\\ \\b \\t suffix
         |  |\\ \\\\ \\\\\\\\ \\b \\t
         |  |'''.stripMargin + str
         |""".stripMargin.fixTripleQuotes,
      s"""val str = "text"
         |<caret>s'''prefix \\\\ \\\\\\\\ \\\\\\\\\\\\\\\\ \\\\b \\\\t
         |   |\\\\ \\\\\\\\ \\\\\\\\\\\\\\\\ \\\\b \\\\t suffix
         |   |\\\\ \\\\\\\\ \\\\\\\\\\\\\\\\ \\\\b \\\\t
         |   |$$str'''.stripMargin
         |""".stripMargin.fixTripleQuotes
    )

  // NOTE: \" is not actually escape in non-interpolated multiline strings ("""string""")
  def testFromConcatenation_WithMultilineString_WithMargins_WithEscapeChar_Quotes(): Unit =
    doTest(
      s"""val str = "text"
         |<caret>'''prefix \\" \\"\\" "\\" \\""
         |  |\\" \\"\\" "\\" \\"" suffix
         |  |\\" \\"\\" "\\" \\""
         |  |'''.stripMargin + str
         |""".stripMargin.fixTripleQuotes,
      s"""val str = "text"
         |<caret>s'''prefix \\\\" \\\\"\\\\" "\\\\" \\\\""
         |   |\\\\" \\\\"\\\\" "\\\\" \\\\"" suffix
         |   |\\\\" \\\\"\\\\" "\\\\" \\\\""
         |   |$$str'''.stripMargin
         |""".stripMargin.fixTripleQuotes
    )

  def testFromConcatenation_WithMultilineString_WithMargins_WithEscapeChar_TripleQuotes(): Unit =
    doTest(
      s"""val str = "text"
         |<caret>'''prefix ""\\u0022 "\\u0022" \\u0022"" \\u0022\\u0022" "\\u0022\\u0022 \\u0022\\u0022\\u0022
         |  |""\\u0022 "\\u0022" \\u0022"" \\u0022\\u0022" "\\u0022\\u0022 \\u0022\\u0022\\u0022 suffix
         |  |""\\u0022 "\\u0022" \\u0022"" \\u0022\\u0022" "\\u0022\\u0022 \\u0022\\u0022\\u0022
         |  |'''.stripMargin + str
         |""".stripMargin.fixTripleQuotes,
      s"""val str = "text"
         |<caret>s'''prefix ""\\" ""\\" ""\\" ""\\" ""\\" ""\\"
         |   |""\\" ""\\" ""\\" ""\\" ""\\" ""\\" suffix
         |   |""\\" ""\\" ""\\" ""\\" ""\\" ""\\"
         |   |$$str'''.stripMargin
         |""".stripMargin.fixTripleQuotes
    )

  def testFromConcatenation_WithMultilineInterpolatedString_WithMargins_WithEscapeUnicode(): Unit =
    doTest(
      s"""val str = "text"
         |<caret>s'''prefix \\\\u0023 \\\\\\u0023 \\\\\\\\u0023
         |   |\\\\u0023 \\\\\\u0023 \\\\\\\\u0023 suffix
         |   |\\\\u0023 \\\\\\u0023 \\\\\\\\u0023
         |   |'''.stripMargin + str
         |""".stripMargin.fixTripleQuotes,
      s"""val str = "text"
         |<caret>s'''prefix \\\\u0023 \\\\# \\\\\\\\u0023
         |   |\\\\u0023 \\\\# \\\\\\\\u0023 suffix
         |   |\\\\u0023 \\\\# \\\\\\\\u0023
         |   |$$str'''.stripMargin
         |""".stripMargin.fixTripleQuotes
    )

  def testFromConcatenation_WithMultilineInterpolatedString_WithMargins_WithEscapeChars(): Unit =
    doTest(
      s"""val str = "text"
         |<caret>s'''prefix \\\\ \\\\\\\\ \\t \\b
         |   |\\\\ \\\\\\\\ \\t \\b suffix
         |   |\\\\ \\\\\\\\ \\t \\b
         |   |'''.stripMargin + str
         |""".stripMargin.fixTripleQuotes,
      s"""val str = "text"
         |<caret>s'''prefix \\\\ \\\\\\\\ \\t \\b
         |   |\\\\ \\\\\\\\ \\t \\b suffix
         |   |\\\\ \\\\\\\\ \\t \\b
         |   |$$str'''.stripMargin
         |""".stripMargin.fixTripleQuotes
    )

  def testFromConcatenation_WithMultilineInterpolatedString_WithMargins_WithEscapeChars_Quotes(): Unit =
    doTest(
      s"""val str = "text"
         |<caret>s'''prefix \\" \\"\\"
         |   |\\" \\"\\" suffix
         |   |'''.stripMargin + str
         |""".stripMargin.fixTripleQuotes,
      s"""val str = "text"
         |<caret>s'''prefix " ""
         |   |" "" suffix
         |   |$$str'''.stripMargin
         |""".stripMargin.fixTripleQuotes
    )

  def testFromConcatenation_WithMultilineInterpolatedString_WithMargins_WithEscapeChars_TripleQuotes(): Unit =
    doTest(
      s"""val str = "text"
         |<caret>s'''prefix 0 \\"\\"\\" 1 "\\"\\" 2 \\""\\" 3 \\"\\"" 4 ""\\" 5 "\\"" 6 "\\u0022"
         |   |0 \\"\\"\\" 1 "\\"\\" 2 \\""\\" 3 \\"\\"" 4 ""\\" 5 "\\"" 6 "\\u0022" suffix
         |   |'''.stripMargin + str
         |""".stripMargin.fixTripleQuotes,
      s"""val str = "text"
         |<caret>s'''prefix 0 ""\\" 1 ""\\" 2 ""\\" 3 ""\\" 4 ""\\" 5 ""\\" 6 ""\\"
         |   |0 ""\\" 1 ""\\" 2 ""\\" 3 ""\\" 4 ""\\" 5 ""\\" 6 ""\\" suffix
         |   |$$str'''.stripMargin
         |""".stripMargin.fixTripleQuotes
    )

  def testFromConcatenation_WithMultilineInterpolatedString_WithMargins_WithPercentChars(): Unit =
    doBulkTest(
      s"""val str = "text"
         |'''prefix % %% %%% %%%% %n
         |   |% %% %%% %%%% %n suffix
         |   |% %% %%% %%%% %n
         |   |'''.stripMargin + str
         |s'''prefix % %% %%% %%%% %n
         |   |% %% %%% %%%% %n suffix
         |   |% %% %%% %%%% %n
         |   |'''.stripMargin + str
         |f'''prefix %% %%%%%n
         |   |%% %%%%%n suffix
         |   |%% %%%%%n
         |   |'''.stripMargin + str
         |""".stripMargin.fixTripleQuotes,
      s"""val str = "text"
         |s'''prefix % %% %%% %%%% %n
         |   |% %% %%% %%%% %n suffix
         |   |% %% %%% %%%% %n
         |   |$$str'''.stripMargin
         |s'''prefix % %% %%% %%%% %n
         |   |% %% %%% %%%% %n suffix
         |   |% %% %%% %%%% %n
         |   |$$str'''.stripMargin
         |s'''prefix % %%
         |   |
         |   |% %%
         |   | suffix
         |   |% %%
         |   |
         |   |$$str'''.stripMargin
         |""".stripMargin.fixTripleQuotes
    )

  //SCL-6567
  def testFromConcatenationWithInjection(): Unit =
    doTest(
      """val str0 = "a"
        |val str1 = "b"
        |val str2 = "c"
        |<caret>str0 + s" $str1 " + str2
        |""".stripMargin,
      """val str0 = "a"
        |val str1 = "b"
        |val str2 = "c"
        |<caret>s"$str0 $str1 $str2"
        |""".stripMargin
    )
}
