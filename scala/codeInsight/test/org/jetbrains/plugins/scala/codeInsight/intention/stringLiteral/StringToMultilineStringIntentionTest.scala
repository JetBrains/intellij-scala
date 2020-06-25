package org.jetbrains.plugins.scala.codeInsight
package intention
package stringLiteral

class StringToMultilineStringIntentionTest extends intentions.ScalaIntentionTestBase {

  override def familyName: String = ScalaCodeInsightBundle.message("family.name.regular.multi.line.string.conversion")

  // ATTENTION:
  //   We shouldn't do .stripMargin for before/after strings because it is for some reason .stripMargin is called inside
  //    ScalaLightCodeInsightFixtureTestAdapter.normalize, that is used in ScalaIntentionTestBase.doTest
  // TODO: cleanup, ideally we should do stripMargin in tests, not somewhere under the hood

  def testConvertMultiline1(): Unit = {
    val before =
      s"""object A {
         |  "one ${CARET}two"
         |}
      """

    val after =
      s"""object A {
         |  \"\"\"one ${CARET}two\"\"\"
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertMultiline2(): Unit = {
    val before =
      s"""object A {
         |  $CARET"one two"
         |}
      """

    val after =
      s"""object A {
         |  $CARET\"\"\"one two\"\"\"
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertEmptyToMultiline(): Unit = {
    val before =
      s"""object A {
         |  "$CARET"
         |}
      """

    val after =
      s"""object A {
         |  \"\"\"$CARET\"\"\"
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline1(): Unit = {
    val before =
      s"""object A {
         |  ${CARET}interp"one$${iterpVal}two"
         |}
      """

    val after =
      s"""object A {
         |  ${CARET}interp\"\"\"one$${iterpVal}two\"\"\"
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline2(): Unit = {
    val before =
      s"""object A {
         |  interp$CARET"one$${iterpVal}two"
         |}
      """

    val after =
      s"""object A {
         |  interp$CARET\"\"\"one$${iterpVal}two\"\"\"
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline3(): Unit = {
    val before =
      s"""object A {
         |  s"one$${iterpVal}${CARET}two"
         |}
      """

    val after =
      s"""object A {
         |  s\"\"\"one$${iterpVal}${CARET}two\"\"\"
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline4(): Unit = {
    val before =
      s"""object A {
         |  interp"one$${iterpVal}${CARET}two"
         |}
      """

    val after =
      s"""object A {
         |  interp\"\"\"one$${iterpVal}${CARET}two\"\"\"
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertInterpolatedMultiline5(): Unit = {
    val before =
      s"""object A {
         |  interp"one$${iterp${CARET}Val}two"
         |}
      """

    val after =
      s"""object A {
         |  interp\"\"\"one$${iterp${CARET}Val}two\"\"\"
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertMultilineWithNewLines1(): Unit = {
    val before =
      s"""object A {
         |  "${CARET}one\\ntwo\\nthree\\nfour"
         |}
      """

    val after =
      s"""object A {
         |  \"\"\"${CARET}one
         |    |two
         |    |three
         |    |four\"\"\".stripMargin
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }


  def testConvertMultilineWithNewLines2(): Unit = {
    val before =
      s"""object A {
         |  "one\\n${CARET}two\\nthree\\nfour"
         |}
      """

    val after =
      s"""object A {
         |  \"\"\"one
         |    |${CARET}two
         |    |three
         |    |four\"\"\".stripMargin
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertMultilineWithNewLines3(): Unit = {
    val before =
      s"""object A {
         |  "one\\ntwo\\nthree\\n${CARET}four"
         |}
      """

    val after =
      s"""object A {
         |  \"\"\"one
         |    |two
         |    |three
         |    |${CARET}four\"\"\".stripMargin
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertMultilineWithNewLines4(): Unit = {
    val before =
      s"""object A {
         |  "one\\ntwo\\nthree\\nfour\\n${CARET}five"
         |}
      """

    val after =
      s"""object A {
         |  \"\"\"one
         |    |two
         |    |three
         |    |four
         |    |${CARET}five\"\"\".stripMargin
         |}
      """

    doTest(before, after)
    doTest(after, before)
  }

  def testConvertFromMultilineWithNewLinesWithoutStripMargin(): Unit = {
    val before =
      s"""object A {
         |  \"\"\"one
         |    |${CARET}two\"\"\"
         |}
      """
    val after =
      s"""object A {
         |  "one\\n    |${CARET}two"
         |}
      """
    doTest(before, after)
  }

  def testConvertFromMultilineWithNewLinesAndShiftedMargins(): Unit = {
    val before =
      s"""object A {
         |  \"\"\"one
         | |two
         |           |${CARET}three\"\"\".stripMargin
         |}
      """
    val after =
      s"""object A {
         |  "one\\ntwo\\n${CARET}three"
         |}
      """
    doTest(before, after)
  }

  def testConvertFromMultilineWithCaretBeforeMarginChar(): Unit = {
    val before =
      s"""object A {
         |  \"\"\"one
         |    |two
         | $CARET   |three\"\"\".stripMargin
         |}
      """
    val after =
      s"""object A {
         |  "one\\ntwo\\n${CARET}three"
         |}
      """
    doTest(before, after)
    doTest(before, after)
  }

  def testConvertMultilineWithNewLinesAndAssigment1(): Unit = {
    val before =
      s"""object A {
         |  val value = $CARET"one\\ntwo\\nthree\\nfour"
         |}
      """

    val after =
      s"""object A {
         |  val value =
         |    $CARET\"\"\"one
         |      |two
         |      |three
         |      |four\"\"\".stripMargin
         |}
      """

    val afterAfter =
      s"""object A {
         |  val value =
         |    $CARET"one\\ntwo\\nthree\\nfour"
         |}
      """

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertMultilineWithNewLinesAndAssigment2(): Unit = {
    val before =
      s"""object A {
         |  val value = "${CARET}one\\ntwo\\nthree\\nfour"
         |}
      """

    val after =
      s"""object A {
         |  val value =
         |    \"\"\"${CARET}one
         |      |two
         |      |three
         |      |four\"\"\".stripMargin
         |}
      """

    val afterAfter =
      s"""object A {
         |  val value =
         |    "${CARET}one\\ntwo\\nthree\\nfour"
         |}
      """

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertMultilineWithNewLinesAndAssigment3(): Unit = {
    val before =
      s"""object A {
         |  val value = "one\\n${CARET}two\\nthree\\nfour"
         |}
      """

    val after =
      s"""object A {
         |  val value =
         |    \"\"\"one
         |      |${CARET}two
         |      |three
         |      |four\"\"\".stripMargin
         |}
      """

    val afterAfter =
      s"""object A {
         |  val value =
         |    "one\\n${CARET}two\\nthree\\nfour"
         |}
      """

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertMultilineWithNewLinesAndAssigment4(): Unit = {
    val before =
      s"""object A {
         |  val value = "one\\ntwo\\nthree\\n${CARET}four"
         |}
      """

    val after =
      s"""object A {
         |  val value =
         |    \"\"\"one
         |      |two
         |      |three
         |      |${CARET}four\"\"\".stripMargin
         |}
      """

    val afterAfter =
      s"""object A {
         |  val value =
         |    "one\\ntwo\\nthree\\n${CARET}four"
         |}
      """

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertInterpolatedMultilineWithNewLinesAndAssigment(): Unit = {
    val before =
      s"""object A {
         |  val value = interp"one\\ntwo\\nthree\\n${CARET}four"
         |}
      """

    val after =
      s"""object A {
         |  val value =
         |    interp\"\"\"one
         |            |two
         |            |three
         |            |${CARET}four\"\"\".stripMargin
         |}
      """

    val afterAfter =
      s"""object A {
         |  val value =
         |    interp"one\\ntwo\\nthree\\n${CARET}four"
         |}
      """

    doTest(before, after)
    doTest(after, afterAfter)
  }

  def testConvertFromInterpolatedStringWithTripleQuotesEscaped(): Unit = {
    getScalaSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = false
    // interpolated string `x` in `before` is invalid due to bug https://github.com/scala/bug/issues/6476
    // but still we would like to fix it by converting to a multiline
    val before =
      s"""val InjectedVar = "42"
         |val x = s"class$CARET A { val a = f\\\"\\\"\\\"$$InjectedVar\\\"\\\"\\\" }"
         |"""
    val after =
      s"""val InjectedVar = "42"
         |val x = s\"\"\"class$CARET A { val a = f\\\"\\\"\\\"$$InjectedVar\\\"\\\"\\\" }\"\"\"
         |"""
    doTest(before, after)
    doTest(after, before.replace(CARET, ""))
  }

  def testConvertFromStringWithTripleQuotesEscaped(): Unit = {
    val before =
      s"""val InjectedVar = "42"
         |val x = "class$CARET A { val a = f\\\"\\\"\\\"$$InjectedVar\\\"\\\"\\\" }"
         |"""
    //original string has to be converted into interpolated string to contain tipple quotes, and injections should be escaped
    val after =
      s"""val InjectedVar = "42"
         |val x = s\"\"\"class$CARET A { val a = f\\\"\\\"\\\"$$$$InjectedVar\\\"\\\"\\\" }\"\"\"
         |"""
    doTest(before, after)
  }

  def testConvertFromStringWithTripleQuotesEscaped_1(): Unit = {
    // FIXME: now it fails, fix is not straightforward, issue is minor
    return
    val before =
      s"""val x = "A \\\"\\\"\\\" $$ $CARET B"
         |""".stripMargin
    val after =
      s"""val x = s\"\"\"A \\\"\\\"\\\" $$$$ $CARET B\"\"\"
         |"""
    doTest(before, after)
  }

  def testConvertFromStringWithDoubleQuotesEscaped(): Unit = {
    val before =
      s"""val InjectedVar = "42"
         |val x = "class$CARET A { val a = f\\\"\\\"$$InjectedVar\\\"\\\" }"
         |"""
    val after =
      s"""val InjectedVar = "42"
         |val x = \"\"\"class$CARET A { val a = f\"\"$$InjectedVar\"\" }\"\"\"
         |"""
    doTest(before, after)
    doTest(after, before.replace(CARET, ""))
  }
}
