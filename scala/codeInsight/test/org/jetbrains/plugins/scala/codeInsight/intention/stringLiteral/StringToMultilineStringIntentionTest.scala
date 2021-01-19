package org.jetbrains.plugins.scala.codeInsight
package intention
package stringLiteral

class StringToMultilineStringIntentionTest extends StringConversionTestBase {

  override def familyName: String = ScalaCodeInsightBundle.message("family.name.regular.multi.line.string.conversion")

  def testConvertMultiline1(): Unit = {
    val before =
      s"""object A {
         |  "one ${CARET}two"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  '''one ${CARET}two'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertMultiline2(): Unit = {
    val before =
      s"""object A {
         |  $CARET"one two"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  $CARET'''one two'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertEmptyToMultiline(): Unit = {
    val before =
      s"""object A {
         |  "$CARET"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  '''$CARET'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline1(): Unit = {
    val before =
      s"""object A {
         |  ${CARET}interp"one$${iterpVal}two"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  ${CARET}interp'''one$${iterpVal}two'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline2(): Unit = {
    val before =
      s"""object A {
         |  interp$CARET"one$${iterpVal}two"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  interp$CARET'''one$${iterpVal}two'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline3(): Unit = {
    val before =
      s"""object A {
         |  s"one$${iterpVal}${CARET}two"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  s'''one$${iterpVal}${CARET}two'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline4(): Unit = {
    val before =
      s"""object A {
         |  interp"one$${iterpVal}${CARET}two"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  interp'''one$${iterpVal}${CARET}two'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline5(): Unit = {
    val before =
      s"""object A {
         |  interp"one$${iterp${CARET}Val}two"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  interp'''one$${iterp${CARET}Val}two'''
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertMultilineWithNewLines1(): Unit = {
    val before =
      s"""object A {
         |  "${CARET}one\\ntwo\\nthree\\nfour"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  '''${CARET}one
         |    |two
         |    |three
         |    |four'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertMultilineWithNewLines2(): Unit = {
    val before =
      s"""object A {
         |  "one\\n${CARET}two\\nthree\\nfour"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  '''one
         |    |${CARET}two
         |    |three
         |    |four'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertMultilineWithNewLines3(): Unit = {
    val before =
      s"""object A {
         |  "one\\ntwo\\nthree\\n${CARET}four"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  '''one
         |    |two
         |    |three
         |    |${CARET}four'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertMultilineWithNewLines4(): Unit = {
    val before =
      s"""object A {
         |  "one\\ntwo\\nthree\\nfour\\n${CARET}five"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  '''one
         |    |two
         |    |three
         |    |four
         |    |${CARET}five'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertFromMultilineWithNewLinesWithoutStripMargin(): Unit = {
    val before =
      s"""object A {
         |  '''one
         |    |${CARET}two'''
         |}
         |""".stripMargin.fixTripleQuotes
    val after =
      s"""object A {
         |  "one\\n    |${CARET}two"
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testConvertFromMultilineWithNewLinesAndShiftedMargins(): Unit = {
    val before =
      s"""object A {
         |  '''one
         | |two
         |           |${CARET}three'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes
    val after =
      s"""object A {
         |  "one\\ntwo\\n${CARET}three"
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testConvertFromMultilineWithCaretBeforeMarginChar(): Unit = {
    val before =
      s"""object A {
         |  '''one
         |    |two
         | $CARET   |three'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes
    val after =
      s"""object A {
         |  "one\\ntwo\\n${CARET}three"
         |}
         |""".stripMargin
    doTest(before, after)
    doTest(before, after)
  }

  def testConvertMultilineWithNewLinesAndAssigment1(): Unit = {
    val before =
      s"""object A {
         |  val value = $CARET"one\\ntwo\\nthree\\nfour"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val value =
         |    $CARET'''one
         |      |two
         |      |three
         |      |four'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes

    val afterAfter =
      s"""object A {
         |  val value =
         |    $CARET"one\\ntwo\\nthree\\nfour"
         |}
         |""".stripMargin

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertMultilineWithNewLinesAndAssigment2(): Unit = {
    val before =
      s"""object A {
         |  val value = "${CARET}one\\ntwo\\nthree\\nfour"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val value =
         |    '''${CARET}one
         |      |two
         |      |three
         |      |four'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes

    val afterAfter =
      s"""object A {
         |  val value =
         |    "${CARET}one\\ntwo\\nthree\\nfour"
         |}
         |""".stripMargin

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertMultilineWithNewLinesAndAssigment3(): Unit = {
    val before =
      s"""object A {
         |  val value = "one\\n${CARET}two\\nthree\\nfour"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val value =
         |    '''one
         |      |${CARET}two
         |      |three
         |      |four'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes

    val afterAfter =
      s"""object A {
         |  val value =
         |    "one\\n${CARET}two\\nthree\\nfour"
         |}
         |""".stripMargin

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertMultilineWithNewLinesAndAssigment4(): Unit = {
    val before =
      s"""object A {
         |  val value = "one\\ntwo\\nthree\\n${CARET}four"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val value =
         |    '''one
         |      |two
         |      |three
         |      |${CARET}four'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes

    val afterAfter =
      s"""object A {
         |  val value =
         |    "one\\ntwo\\nthree\\n${CARET}four"
         |}
         |""".stripMargin

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertInterpolatedMultilineWithNewLinesAndAssigment(): Unit = {
    val before =
      s"""object A {
         |  val value = interp"one\\ntwo\\nthree\\n${CARET}four"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val value =
         |    interp'''one
         |            |two
         |            |three
         |            |${CARET}four'''.stripMargin
         |}
         |""".stripMargin.fixTripleQuotes

    val afterAfter =
      s"""object A {
         |  val value =
         |    interp"one\\ntwo\\nthree\\n${CARET}four"
         |}
         |""".stripMargin

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertFromInterpolatedStringWithTripleQuotesEscaped(): Unit = {
    getScalaSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = false
    // interpolated string `x` in `before` is invalid due to bug https://github.com/scala/bug/issues/6476
    // but still we would like to fix it by converting to a multiline
    val before =
      s"""val InjectedVar = "42"
         |val x = s"class$CARET A { val a = f\\"\\"\\"$$InjectedVar\\"\\"\\" }"
         |""".stripMargin
    val after =
      s"""val InjectedVar = "42"
         |val x = s""\"class$CARET A { val a = f""\\"$$InjectedVar""\\" }""\"
         |""".stripMargin
    doTest(before, after)
    doTest(after, before.replace(CARET, ""))
  }

  def testConvertFromInterpolatedStringWithTripleQuotesEscaped_1(): Unit = {
    val before = s"""s'''start ""\\" "\\"\\" \\"\\"\\" \\"\\"" \\""\\" end'''""".fixTripleQuotes
    val after1  = s"""s"start \\"\\"\\" \\"\\"\\" \\"\\"\\" \\"\\"\\" \\"\\"\\" end""""
    val after2  = s"""s'''start ""\\" ""\\" ""\\" ""\\" ""\\" end'''""".fixTripleQuotes
    doTest(before, after1)
    doTest(after1, after2)
  }

  def testConvertFromInterpolatedStringWithTripleQuotesEscaped_2(): Unit = {
    val before = s"""s"start \\"\\"\\" end""""
    val after = s"""s'''start ""\\" end'''""".fixTripleQuotes
    doTest(before, after)
  }

  def testConvertFromInterpolatedStringWithTripleQuotesEscaped_3_RawInterpolator(): Unit = {
    getScalaSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = false

    // NOTE \" is not actually an escape in raw"string"
    val before = s"""raw'''start ""\\u0022 "\\u0022" \\u0022"" end'''""".fixTripleQuotes
    // such magic is required to encode \" due to bug in compiler: https://github.com/scala/bug/issues/6476
    val after1 = s"""raw"start \\u0022\\u0022\\u0022 \\u0022\\u0022\\u0022 \\u0022\\u0022\\u0022 end""""
    val after2 = s"""raw'''start ""\\u0022 ""\\u0022 ""\\u0022 end'''""".fixTripleQuotes

    doTest(before, after1)
    doTest(after1, after2)
  }

  def testConvertFromInterpolatedStringWithQuotesEscaped_RawInterpolator(): Unit = {
    getScalaSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = false

    // NOTE \" is not actually an escape in raw"string"
    val before = s"""raw'''start " "" \\" \\"\\" \\"\\"\\" ""\\" "\\"" end'''""".fixTripleQuotes
    // such magic is required to encode \" due to bug in compiler: https://github.com/scala/bug/issues/6476
    val after1 = s"""raw"start \\u0022 \\u0022\\u0022 \\u005c\\u0022 \\u005c\\u0022\\u005c\\u0022 \\u005c\\u0022\\u005c\\u0022\\u005c\\u0022 \\u0022\\u0022\\u005c\\u0022 \\u0022\\u005c\\u0022\\u0022 end""""
    val after2 = s"""raw'''start " "" \\" \\"\\" \\"\\"\\" ""\\" "\\"" end'''""".fixTripleQuotes

    doTest(before, after1)
    doTest(after1, after2)
  }

  def testConvertFromInterpolatedStringWithQuotesEscaped_RawInterpolator_1(): Unit = {
    getScalaSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = false

    val before =
      s"""raw'''"'''
         |raw'''""'''
         |raw'''" ""'''
         |raw'''\\" \\"\\" \\\\"'''
         |""".stripMargin.fixTripleQuotes
    val after1 =
      s"""raw"\\u0022"
         |raw"\\u0022\\u0022"
         |raw"\\u0022 \\u0022\\u0022"
         |raw"\\u005c\\u0022 \\u005c\\u0022\\u005c\\u0022 \\\\\\u0022"
         |""".stripMargin.fixTripleQuotes
    val after2 =
      s"""raw'''"'''
         |raw'''""'''
         |raw'''" ""'''
         |raw'''\\" \\"\\" \\\\"'''
         |""".stripMargin.fixTripleQuotes

    doBulkTest(before, after1)
    doBulkTest(after1, after2)
  }

  def testConvertFromStringWithTripleQuotesEscaped(): Unit = {
    val before =
      s"""val InjectedVar = "42"
         |val x = "class$CARET A { val a = f\\"\\"\\"$$InjectedVar\\"\\"\\" }"
         |""".stripMargin
    val after =
      s"""val InjectedVar = "42"
         |val x = '''class$CARET A { val a = f""\\u0022$$InjectedVar""\\u0022 }'''
         |""".stripMargin.fixTripleQuotes
    doTest(before, after)
  }

  def testConvertFromStringWithTripleQuotesEscaped_1(): Unit = {
    val before = s""""A \\"\\"\\" $$ B""""
    val after = s"""'''A ""\\u0022 $$ B'''""".fixTripleQuotes
    doTest(before, after)
    doTest(after, before)
  }

  def testConvertFromStringWithTripleQuotesEscaped_2(): Unit = {
    // NOTE \" is not actually an escape in raw"string"
    val before = s"""'''start ""\\u0022 "\\u0022" \\u0022"" end'''""".fixTripleQuotes
    val after1 = s""""start \\"\\"\\" \\"\\"\\" \\"\\"\\" end""""
    val after2 = s"""'''start ""\\u0022 ""\\u0022 ""\\u0022 end'''""".fixTripleQuotes

    doTest(before, after1)
    doTest(after1, after2)
  }

  def testConvertFromStringWithQuotesEscaped(): Unit = {
    // NOTE \" is not actually an escape in raw"string"
    val before = """'''start " "" \" \\" \\\" \\\\" \"\"\" end'''""".fixTripleQuotes
    val after1 = """"start \" \"\" \\\" \\\\\" \\\\\\\" \\\\\\\\\" \\\"\\\"\\\" end"""".fixTripleQuotes
    val after2 = """'''start " "" \" \\" \\\" \\\\" \"\"\" end'''""".fixTripleQuotes

    doTest(before, after1)
    doTest(after1, after2)
  }

  def testConvertFromStringWithDoubleQuotesEscaped(): Unit = {
    val before =
      s"""val InjectedVar = "42"
         |val x = "class$CARET A { val a = f\\"\\"$$InjectedVar\\"\\" }"
         |""".stripMargin
    val after =
      s"""val InjectedVar = "42"
         |val x = '''class$CARET A { val a = f""$$InjectedVar"" }'''
         |""".stripMargin.fixTripleQuotes
    doTest(before, after)
    doTest(after, before.replace(CARET, ""))
  }

  def test_SCL_18615(): Unit =
    doBulkTest(
      s""""\\u0023 \\u0024 \\u0025"
         |s"\\u0023 \\u0024 \\u0025"
         |raw"\\u0023 \\u0024 \\u0025"
         |f"\\u0023 \\u0024 \\u0025\\u0025"
         |
         |"\\\\u0023 \\\\u0024 \\\\u0025"
         |s"\\\\u0023 \\\\u0024 \\\\u0025"
         |raw"\\\\u0023 \\\\u0024 \\\\u0025"
         |f"\\\\u0023 \\\\u0024 \\\\u0025"""".stripMargin,
      s"""'''# $$ %'''
         |s'''# $$$$ %'''
         |raw'''# $$$$ %'''
         |f'''# $$$$ %%'''
         |
         |'''\\u005cu0023 \\u005cu0024 \\u005cu0025'''
         |s'''\\\\u0023 \\\\u0024 \\\\u0025'''
         |raw'''\\\\u0023 \\\\u0024 \\\\u0025'''
         |f'''\\\\u0023 \\\\u0024 \\\\u0025'''""".stripMargin.fixTripleQuotes
    )

  def test_SCL_18615_1(): Unit =
    doBulkTest(
      s""""\\\\u0023 \\\\\\\\u0023 \\\\\\\\\\\\u0023 \\\\\\\\\\\\\\\\u0023 \\\\\\\\\\\\\\\\\\\\u0023 \\\\\\\\\\\\\\\\\\\\\\\\u0023"
         |"\\\\u0023 \\t\\\\u0023 \\t\\t\\\\u0023 \\\\t\\\\u0023 \\\\t\\\\t\\\\u0023"
         |"\\\\"
         |"\\\\\\\\"
         |"\\\\\\\\\\\\"
         |""".stripMargin,
      s"""'''\\u005cu0023 \\\\u0023 \\\\\\u005cu0023 \\\\\\\\u0023 \\\\\\\\\\u005cu0023 \\\\\\\\\\\\u0023'''
         |'''\\u005cu0023 	\\u005cu0023 		\\u005cu0023 \\t\\u005cu0023 \\t\\t\\u005cu0023'''
         |'''\\'''
         |'''\\\\'''
         |'''\\\\\\'''
         |""".stripMargin.fixTripleQuotes
    )

  def test_SCL_18615_2_MultipleU(): Unit =
    doBulkTest(
      s""""\\\\uu0023 \\\\\\\\uuu0023 \\\\\\\\\\\\uuuu0023 \\\\\\\\\\\\\\\\uuuuu0023 \\\\\\\\\\\\\\\\\\\\uuuuu0023"
         |""".stripMargin,
      s"""'''\\u005cuu0023 \\\\uuu0023 \\\\\\u005cuuuu0023 \\\\\\\\uuuuu0023 \\\\\\\\\\u005cuuuuu0023'''
         |""".stripMargin.fixTripleQuotes
    )

  def test_SCL_18615_3_WithUsualText(): Unit =
    doBulkTest(
      s""""abc   \\\\u0023   def   \\\\\\\\u0023   ghi"
         |""".stripMargin,
      s"""'''abc   \\u005cu0023   def   \\\\u0023   ghi'''
         |""".stripMargin.fixTripleQuotes
    )

  def test_SCL_16500(): Unit = {
    val before = s""""\\uBEEF""""
    val after1 = s"""'''뻯'''""".fixTripleQuotes
    val after2 = s""""뻯"""".fixTripleQuotes
    doTest(before, after1)
    doTest(after1, after2)
  }
}
