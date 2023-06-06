package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

import com.intellij.profile.codeInspection.InspectionProfileManager
import org.jetbrains.plugins.scala.codeInspection.parentheses.{ScalaUnnecessaryParenthesesInspection, UnnecessaryParenthesesSettings}

// TODO: rewrite the tests: use file-based test cases to avoid all this backslashes escaping staff...
class ConvertToStringConcatenationActionTest extends StringConversionTestBase {

  override def familyName: String = FormatConversionIntention.ConvertToStringConcat

  override def setUp(): Unit = {
    super.setUp()
    // we apply ScalaUnnecessaryParenthesesInspection when converting to concatenation
    myFixture.enableInspections(classOf[ScalaUnnecessaryParenthesesInspection])
  }

  override protected def tearDown(): Unit =
    super.tearDown()

  def testFromInterpolated(): Unit = {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET"one " + x + " two"
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testFrom_Empty(): Unit = {
    val EMPTY = s""""$CARET""""
    doTest(s"""s"$CARET"""", EMPTY)
    doTest(f"""s"$CARET"""", EMPTY)
    doTest(raw"""s"$CARET"""", EMPTY)
  }

  def testFromInterpolated_WrapWholeConcatWithBraces_WithMethodCall(): Unit = {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two".length
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET("one " + x + " two").length
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testFromInterpolated_WrapWholeConcatWithBraces_WithMethodCall2(): Unit = {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two".substring(23)
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET("one " + x + " two").substring(23)
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testFromInterpolated_WrapWholeConcatWithBraces_WithPostfixMethodCall(): Unit = {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two" length
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET("one " + x + " two") length
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testFromInterpolated_WrapWholeConcatWithBraces_WithInfixMethodCall(): Unit = {
    val before =
      s"""object A {
         |  val x = 42
         |  obj foo ${CARET}s"one $$x two"
         |}
         |""".stripMargin

    val after =
      s"""object A {
         |  val x = 42
         |  obj foo $CARET("one " + x + " two")
         |}
         |""".stripMargin

    doTest(before, after)
  }

  def testFromInterpolated_EmptyBlockInjection(): Unit = {
    doBulkTest(
      """s"${}"
        |s"${{}}"
        |s"${{{}}}"
        |s"${}${}"
        |
        |s"a ${}"
        |s"a ${{}}"
        |s"a ${}${}"
        |
        |s"${} b"
        |s"${{}} b"
        |s"${}${} b"
        |""".stripMargin,
      """().toString
        |().toString
        |{}.toString
        |().toString + ()
        |
        |"a " + ()
        |"a " + ()
        |"a " + () + ()
        |
        |().toString + " b"
        |().toString + " b"
        |().toString + () + " b"
        |""".stripMargin
    )
  }

  // SCL-18587
  def testFromInterpolated_WithPercents(): Unit =
    doTest(
      """s"% %% %%% ${1} %%% %% %"""",
      """"% %% %%% " + 1 + " %%% %% %""""
    )

  def testFromInterpolated_WithPercents_WithKindaFormatEscapes(): Unit =
    doTest(
      """s"%% %%%% ${1}%d %% %n %%n %%%n %%%%n"""",
      """"%% %%%% " + 1 + "%d %% %n %%n %%%n %%%%n""""
    )

  def testFromFormatInterpolated_ShouldUnescapeDollarAndLineSeparator(): Unit =
    doTest(
      """f"%% %%%% ${1}%d %% %n %%n %%%n %%%%n"""",
      """"% %% " + 1 + " % \n %n %\n %%n""""
    )

  def testFromFormatted_ShouldUnescapeDollarAndLineSeparator(): Unit =
    doTest(
      """"%% %%%% %d %% %n %%n %%%n %%%%n".format(1)""",
      """"% %% " + 1 + " % \n %n %\n %%n""""
    )

  private def doTestWithPredefinedValues(text: String, resultText: String): Unit = {
    val prefix = """val s: String = "s1"
                   |val s2: String = "s2"
                   |val x: Int = 42
                   |val floatV: Float = 23f
                   |<caret>""".stripMargin
    doTest(prefix + text, prefix + resultText)
  }

  def testFromFormatted_ShouldUnescapeDollarAndLineSeparator_1(): Unit = {
    doTestWithPredefinedValues(
      """"%% %%%% %2.2f %% %n %%n".format(floatV)""".stripMargin,
      """"% %% " + floatV.formatted("%2.2f") + " % \n %n"""".stripMargin
    )
    doTestWithPredefinedValues(
      """"%% %%%% %2.2f %% %n %%n".format(1 + 2 + 3)""".stripMargin,
      """"% %% " + (1 + 2 + 3).formatted("%2.2f") + " % \n %n"""".stripMargin
    )
  }

  def testFromInterpolated_WithoutInjectedArithmetics(): Unit =
    doTestWithPredefinedValues(
      """s"text ${42} $s $x ${s} ${x}"""",
      """"text " + 42 + " " + s + " " + x + " " + s + " " + x"""
    )

  // SCL-18586
  def testFromInterpolated_WrapInjectionWithParenthesis(): Unit =
    doBulkTest(
      """var int: Int = 0
        |var str: Int = 0
        |
        |Seq(
        |  s"a $int b",
        |  s"a ${int} b",
        |  s"a ${int}${int}${int} b",
        |  s"a ${int + int}${int + int}${int + int} b",
        |  s"a ${str + str} b",
        |  s"a ${str + str}${str + str}${str + str} b",
        |  s"a ${"x" + "y"} b",
        |  s"a ${2 + 3} b",
        |  s"a ${2 - 3} b",
        |  s"a ${2 * 3} b",
        |  s"a ${2 / 3} b",
        |  s"a ${2 % 3} b",
        |  s"a ${2: AnyVal} b",
        |  s"a ${2 < 3} b",
        |  s"a ${2 > 3} b",
        |  s"a ${2 == 3} b",
        |  s"a ${2 != 3} b",
        |  s"a ${2 >> 3} b",
        |  s"a ${2 >>> 3} b",
        |  s"a ${2 << 3} b",
        |  s"a ${true || false} b",
        |  s"a ${true && false} b",
        |  s"a ${true | false} b",
        |  s"a ${true & false} b",
        |  s"a ${true ^ false} b",
        |  s"a ${int; int; int} b",
        |)
        |""".stripMargin,
      """var int: Int = 0
        |var str: Int = 0
        |
        |Seq(
        |  "a " + int + " b",
        |  "a " + int + " b",
        |  "a " + int + int + int + " b",
        |  "a " + (int + int) + (int + int) + (int + int) + " b",
        |  "a " + (str + str) + " b",
        |  "a " + (str + str) + (str + str) + (str + str) + " b",
        |  "a " + ("x" + "y") + " b",
        |  "a " + (2 + 3) + " b",
        |  "a " + (2 - 3) + " b",
        |  "a " + (2 * 3) + " b",
        |  "a " + (2 / 3) + " b",
        |  "a " + (2 % 3) + " b",
        |  "a " + (2: AnyVal) + " b",
        |  "a " + (2 < 3) + " b",
        |  "a " + (2 > 3) + " b",
        |  "a " + (2 == 3) + " b",
        |  "a " + (2 != 3) + " b",
        |  "a " + (2 >> 3) + " b",
        |  "a " + (2 >>> 3) + " b",
        |  "a " + (2 << 3) + " b",
        |  "a " + (true || false) + " b",
        |  "a " + (true && false) + " b",
        |  "a " + (true | false) + " b",
        |  "a " + (true & false) + " b",
        |  "a " + (true ^ false) + " b",
        |  "a " + {
        |    int;
        |    int;
        |    int
        |  } + " b",
        |)
        |""".stripMargin
    )

  def testFromInterpolated_WrapInjectionWithParenthesis_IgnoreClarifying(): Unit = {
    val inspection = InspectionProfileManager.getInstance(getProject)
      .getCurrentProfile
      .getInspectionTool("ScalaUnnecessaryParentheses", getProject)
      .getTool
      .asInstanceOf[ScalaUnnecessaryParenthesesInspection]

    val settingsBefore: UnnecessaryParenthesesSettings = inspection.currentSettings()

    inspection.setSettings(settingsBefore.copy(ignoreClarifying = false))
    doBulkTest(
      """var int: Int = 0
        |var str: Int = 0
        |
        |Seq(
        |  s"a $int b",
        |  s"a ${int} b",
        |  s"a ${int}${int}${int} b",
        |  s"a ${int + int}${int + int}${int + int} b",
        |  s"a ${str + str} b",
        |  s"a ${str + str}${str + str}${str + str} b",
        |  s"a ${"x" + "y"} b",
        |  s"a ${2 + 3} b",
        |  s"a ${2 - 3} b",
        |  s"a ${2 * 3} b",
        |  s"a ${2 / 3} b",
        |  s"a ${2 % 3} b",
        |  s"a ${2: AnyVal} b",
        |  s"a ${2 < 3} b",
        |  s"a ${2 > 3} b",
        |  s"a ${2 == 3} b",
        |  s"a ${2 != 3} b",
        |  s"a ${2 >> 3} b",
        |  s"a ${2 >>> 3} b",
        |  s"a ${2 << 3} b",
        |  s"a ${true || false} b",
        |  s"a ${true && false} b",
        |  s"a ${true | false} b",
        |  s"a ${true & false} b",
        |  s"a ${true ^ false} b",
        |  s"a ${int; int; int} b",
        |)
        |""".stripMargin,
      """var int: Int = 0
        |var str: Int = 0
        |
        |Seq(
        |  "a " + int + " b",
        |  "a " + int + " b",
        |  "a " + int + int + int + " b",
        |  "a " + (int + int) + (int + int) + (int + int) + " b",
        |  "a " + (str + str) + " b",
        |  "a " + (str + str) + (str + str) + (str + str) + " b",
        |  "a " + ("x" + "y") + " b",
        |  "a " + (2 + 3) + " b",
        |  "a " + (2 - 3) + " b",
        |  "a " + 2 * 3 + " b",
        |  "a " + 2 / 3 + " b",
        |  "a " + 2 % 3 + " b",
        |  "a " + (2: AnyVal) + " b",
        |  "a " + (2 < 3) + " b",
        |  "a " + (2 > 3) + " b",
        |  "a " + (2 == 3) + " b",
        |  "a " + (2 != 3) + " b",
        |  "a " + (2 >> 3) + " b",
        |  "a " + (2 >>> 3) + " b",
        |  "a " + (2 << 3) + " b",
        |  "a " + (true || false) + " b",
        |  "a " + (true && false) + " b",
        |  "a " + (true | false) + " b",
        |  "a " + (true & false) + " b",
        |  "a " + (true ^ false) + " b",
        |  "a " + {
        |    int;
        |    int;
        |    int
        |  } + " b",
        |)
        |""".stripMargin
    )

    inspection.setSettings(settingsBefore)
  }

  // SCL-18608
  def testFirstOperandShouldBeString(): Unit =
    doBulkTest(
      """class A {
        |  val int: Int = 42
        |  val str: String = "value"
        |
        |  s"${42}"
        |  s"${2 + 2}"
        |  s"$int"
        |  s"$int text"
        |  s"$int$int"
        |  s"$int$int text"
        |
        |  s"$str"
        |  s"$str$str"
        |  s"$str$int"
        |  s"$str${42}"
        |
        |  s"${int}${42}"
        |  s"${42}${int}"
        |  s"${42}${str}"
        |  s"${42}${str}${int}"
        |  s"${int}${str}${42}"
        |  s"${int}${42}${str}"
        |
        |  f"${42}%+d"
        |  f"${int}%+d${int + int}%+d"
        |}
        |""".stripMargin,
      """class A {
        |  val int: Int = 42
        |  val str: String = "value"
        |
        |  42.toString
        |  (2 + 2).toString
        |  int.toString
        |  int.toString + " text"
        |  int.toString + int
        |  int.toString + int + " text"
        |
        |  str
        |  str + str
        |  str + int
        |  str + 42
        |
        |  int.toString + 42
        |  42.toString + int
        |  42.toString + str
        |  42.toString + str + int
        |  int.toString + str + 42
        |  int.toString + 42 + str
        |
        |  42.formatted("%+d")
        |  int.formatted("%+d") + (int + int).formatted("%+d")
        |}
        |""".stripMargin
    )

  // combination of SCL-18586 and SCL-18608
  def testtestFirstOperandShouldBeString_1(): Unit =
    doBulkTest(
      """val int: Int = 42
        |val str: String = "value"
        |
        |s"${int}${int + int}"
        |s"${int + int}${int}"
        |s"${int + int}${int + int}"
        |
        |s"${str + int + int}${int + int}"
        |s"${int + str + int}${int + int}"
        |s"${int + int + str}${int + int}"
        |
        |s"${int + int}${str + int + int}"
        |s"${int + int}${int + str + int}"
        |s"${int + int}${int + int + str}"
        |
        |s"${int + int + str}${int + int + str}"
        |""".stripMargin,
      """val int: Int = 42
        |val str: String = "value"
        |
        |int.toString + (int + int)
        |(int + int).toString + int
        |(int + int).toString + (int + int)
        |
        |(str + int + int) + (int + int)
        |(int + str + int) + (int + int)
        |(int + int + str) + (int + int)
        |
        |(int + int).toString + (str + int + int)
        |(int + int).toString + (int + str + int)
        |(int + int).toString + (int + int + str)
        |
        |(int + int + str) + (int + int + str)
        |""".stripMargin
    )

  def testFirstOperandShouldBeString_2_DifferentTypes(): Unit =
    doBulkTest(
      """//AnyVal
        |val int: Int = 42
        |val byte: Byte = 42
        |val char: Char = 'x'
        |val unit: Unit = ()
        |val bool: Boolean = false
        |val long: Long = 42L
        |val short: Short = 42
        |val float: Float = 42f
        |val double: Double = 42.0
        |
        |//AnyRef
        |val str: String = null
        |val obj: Object = null
        |val arr: Array[Int] = null
        |val seq: Seq[Int] = null
        |val chars: Array[Char] = null
        |
        |//AnyVal usage
        |s"$int text"
        |s"$byte text"
        |s"$char text"
        |s"$unit text"
        |s"${()} text"
        |s"$bool text"
        |s"$long text"
        |s"$short text"
        |s"$float text"
        |s"$double text"
        |
        |//AnyRef usage
        |s"$str text"
        |s"$obj text"
        |s"$arr text"
        |s"$seq text"
        |s"$chars text"
        |s"${"".toCharArray} text"
        |""".stripMargin,
      """//AnyVal
        |val int: Int = 42
        |val byte: Byte = 42
        |val char: Char = 'x'
        |val unit: Unit = ()
        |val bool: Boolean = false
        |val long: Long = 42L
        |val short: Short = 42
        |val float: Float = 42f
        |val double: Double = 42.0
        |
        |//AnyRef
        |val str: String = null
        |val obj: Object = null
        |val arr: Array[Int] = null
        |val seq: Seq[Int] = null
        |val chars: Array[Char] = null
        |
        |//AnyVal usage
        |int.toString + " text"
        |byte.toString + " text"
        |char.toString + " text"
        |unit.toString + " text"
        |().toString + " text"
        |bool.toString + " text"
        |long.toString + " text"
        |short.toString + " text"
        |float.toString + " text"
        |double.toString + " text"
        |
        |//AnyRef usage
        |str + " text"
        |String.valueOf(obj) + " text"
        |String.valueOf(arr) + " text"
        |String.valueOf(seq) + " text"
        |String.valueOf(chars: AnyRef) + " text"
        |String.valueOf("".toCharArray: AnyRef) + " text"
        |""".stripMargin
    )

  def testFirstOperandShouldBeString_3_Literals(): Unit =
    doBulkTest(
      """s"${42} text"
        |s"${42f} text"
        |s"${42.0} text"
        |s"${()} text"
        |s"${true} text"
        |s"${42L} text"
        |s"${"str"} text"
        |""".stripMargin,
      """42.toString + " text"
        |42f.toString + " text"
        |42.0.toString + " text"
        |().toString + " text"
        |true.toString + " text"
        |42L.toString + " text"
        |"str" + " text"
        |""".stripMargin
    )

  def testFirstOperandShouldBeString_4_InjectionNeedsBrackets(): Unit =
    doBulkTest(
      """val int: Int = 42
        |
        |// infix
        |s"${int + int + int} text"
        |s"${int * int * int} text"
        |s"${int * int + int} text"
        |s"${int + int * int} text"
        |s"${int / int / int} text"
        |s"${int / int + int} text"
        |s"${int + int / int} text"
        |""".stripMargin,
      """val int: Int = 42
        |
        |// infix
        |(int + int + int).toString + " text"
        |(int * int * int).toString + " text"
        |(int * int + int).toString + " text"
        |(int + int * int).toString + " text"
        |(int / int / int).toString + " text"
        |(int / int + int).toString + " text"
        |(int + int / int).toString + " text"
        |""".stripMargin
    )

  // combination of SCL-15420, SCL-18586, SCL-18608
  def testMixed_SCL_15420_SCL_18586_SCL_18608(): Unit ={
    doBulkTest(
      """val int = 42
        |val str = "text"
        |
        |s"${1}${2}${3}".length
        |s"$int$int${int}".length
        |s"${1 + 1}${2 + 2}${3 + 3}".length
        |s"${int + int}${int + int}${int + int}".length
        |s"$str$str${str}".length
        |s"${str + str}${str + str}${str + str}".length
        |""".stripMargin,
      """val int = 42
        |val str = "text"
        |
        |(1.toString + 2 + 3).length
        |(int.toString + int + int).length
        |((1 + 1).toString + (2 + 2) + (3 + 3)).length
        |((int + int).toString + (int + int) + (int + int)).length
        |(str + str + str).length
        |((str + str) + (str + str) + (str + str)).length
        |""".stripMargin
    )
  }

  //SCL-18617, SCL-18583
  def testFromInterpolated_WithEscapeSequences(): Unit =
    doBulkTest(
      s"""s"\\\\ $${42} \\\\ \\t"
         |s'''\\\\ $${42} \\\\ \\t'''
         |f"\\\\ $${42} \\\\ \\t"
         |f'''\\\\ $${42} \\\\ \\t'''
         |raw"\\ \\\\ $${42} \\\\ \\t"
         |raw'''\\ \\\\ $${42} \\\\ \\t'''
         |""".stripMargin.fixTripleQuotes,
      s""""\\\\ " + 42 + " \\\\ \\t"
        |"\\\\ " + 42 + " \\\\ \\t"
        |"\\\\ " + 42 + " \\\\ \\t"
        |"\\\\ " + 42 + " \\\\ \\t"
        |"\\\\ \\\\\\\\ " + 42 + " \\\\\\\\ \\\\t"
        |"\\\\ \\\\\\\\ " + 42 + " \\\\\\\\ \\\\t"
        |""".stripMargin.fixTripleQuotes
    )

  def testFromInterpolated_WithEscapeSequences_UnicodeEscape(): Unit =
    doBulkTest(
      s"""val str = " text"
         |
         |s"\\u0023 \\u0024 \\u0025 $$str"
         |s'''\\u0023 \\u0024 \\u0025 $$str'''
         |f"\\u0023 \\u0024 \\u0025\\u0025 $$str"
         |f'''\\u0023 \\u0024 \\u0025\\u0025 $$str'''
         |raw"\\u0023 \\u0024 \\u0025 $$str"
         |raw'''\\u0023 \\u0024 \\u0025 $$str'''
         |""".stripMargin.fixTripleQuotes,
      """val str = " text"
        |
        |"# $ % " + str
        |"# $ % " + str
        |"# $ % " + str
        |"# $ % " + str
        |"# $ % " + str
        |"# $ % " + str
        |""".stripMargin.fixTripleQuotes
    )

  // Mind this weird compiler behaviour: https://github.com/scala/bug/issues/12293#issuecomment-760276225
  // raw"\u0025" == "%"
  // raw"\\u0025" == "\\u0025"
  // raw"\\\u0025" == "\\%"
  // raw"\\\\u0025" == "\\\\u0025"
  def testFromInterpolated_WithEscapeSequences_UnicodeEscape_WeirdCase(): Unit =
    doBulkTest(
      s"""val str = " text"
         |
         |s"text \\\\u0023 $$str"
         |s'''text \\\\u0023 $$str'''
         |f"text \\\\u0023 $$str"
         |f'''text \\\\u0023 $$str'''
         |raw"text \\\\u0023 $$str"
         |raw'''text \\\\u0023 $$str'''
         |""".stripMargin.fixTripleQuotes,
      s"""val str = " text"
         |
         |"text \\\\u0023 " + str
         |"text \\\\u0023 " + str
         |"text \\\\u0023 " + str
         |"text \\\\u0023 " + str
         |"text \\\\\\\\u0023 " + str
         |"text \\\\\\\\u0023 " + str
         |""".stripMargin.fixTripleQuotes
    )

  def testFromFormatted_WithEscapeSequences(): Unit =
    doBulkTest(
      s""""\\b \\t \\n \\f \\r \\\\ \\" \\n".format()""",
      s""""\\b \\t \\n \\f \\r \\\\ \\" \\n""""
    )

  def testFromFormatted_WithEscapeSequences_RawContent(): Unit =
    doBulkTest(
      s"""'''\\b \\t \\n \\f \\r \\\\ \\\\" \\n'''.format()""".fixTripleQuotes,
      s""""\\\\b \\\\t \\\\n \\\\f \\\\r \\\\\\\\ \\\\\\\\\\" \\\\n""""
    )

  def testFromFormatted_WithEscapeSequences_WithUnicode(): Unit =
    doBulkTest(
      s""""\\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023".format()""",
      s""""# \\\\u0023 \\\\# \\\\\\\\u0023""""
    )

  def testFromFormatted_WithEscapeSequences_WithUnicode_RawContent(): Unit =
    doBulkTest(
      s"""'''\\u0023 \\\\u0023'''.format()""".fixTripleQuotes,
      s""""# \\\\\\\\u0023""""
    )

  def testFromFormatted_WithMultilineStringWithMargins_WithEscapeChar(): Unit =
    doBulkTest(
      """'''prefix \b \t \n suffix
         |  |prefix \f \r \\ " \" \\" \n suffix
         |  |'''.stripMargin.format()
         |""".stripMargin.fixTripleQuotes,
      """("prefix \\b \\t \\n suffix\nprefix \\f \\r \\\\ \" \\\" \\\\\" \\n suffix\n").stripMargin.format()
         |""".stripMargin.fixTripleQuotes
    )

  def testFromFormatted_WithMultilineStringWithMargins_WithEscapeUnicode(): Unit =
    doBulkTest(
      s"""'''prefix \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 suffix
         |  |prefix \\u0023 \\\\u0023 \\\\\\u0023 \\\\\\\\u0023 suffix
         |  |'''.stripMargin.format()
         |""".stripMargin.fixTripleQuotes,
      s"""("prefix # \\\\\\\\u0023 \\\\\\\\# \\\\\\\\\\\\\\\\u0023 suffix\\nprefix # \\\\\\\\u0023 \\\\\\\\# \\\\\\\\\\\\\\\\u0023 suffix\\n").stripMargin.format()
         |""".stripMargin.fixTripleQuotes
    )

  // Yes I know, it's very huge and clumsy...
  def testFromInterpolated_WithMultilineStringWithMargins_WithEscapeChar(): Unit =
    doBulkTest(
      """val str = " text"
         |
         |s'''prefix \\ ${42} \\ \t " \" \\" "" \n
         |   |\\ ${42} \\ \t " \" \\" "" \n suffix
         |   |'''.stripMargin
         |f'''prefix \\ ${42} \\ \t " \" \\" "" \n
         |   |\\ ${42} \\ \t " \" \\" "" \n suffix
         |   |'''.stripMargin
         |raw'''prefix \ \\ ${42} \\ \t " \" \\" "" \n
         |     |\ \\ ${42} \\ \t " \" \\" "" \n suffix
         |     |'''.stripMargin
         |""".stripMargin.fixTripleQuotes,
      """val str = " text"
         |
         |("prefix \\ " + 42 + " \\ \t \" \" \\\" \"\" \n\n\\ " + 42 + " \\ \t \" \" \\\" \"\" \n suffix\n").stripMargin
         |("prefix \\ " + 42 + " \\ \t \" \" \\\" \"\" \n\n\\ " + 42 + " \\ \t \" \" \\\" \"\" \n suffix\n").stripMargin
         |("prefix \\ \\\\ " + 42 + " \\\\ \\t \" \\\" \\\\\" \"\" \\n\n\\ \\\\ " + 42 + " \\\\ \\t \" \\\" \\\\\" \"\" \\n suffix\n").stripMargin
         |""".stripMargin
    )

  // Yes I know, it's very huge and clumsy...
  def testFromInterpolated_WithMultilineStringWithMargins_WithEscapeUnicode(): Unit =
    doBulkTest(
      s"""val str = " text"
         |
         |s'''prefix \\u0023 \\u0024 \\u0025 $$str \\\\u0023 $$str
         |   |\\u0023 \\u0024 \\u0025 $$str \\\\u0023 $$str suffix
         |   |\\u0023 \\u0024 \\u0025 $$str \\\\u0023 $$str
         |   |'''.stripMargin
         |f'''prefix \\u0023 \\u0024 \\u0025\\u0025 $$str \\\\u0023 $$str
         |   |\\u0023 \\u0024 \\u0025\\u0025 $$str \\\\u0023 $$str suffix
         |   |\\u0023 \\u0024 \\u0025\\u0025 $$str \\\\u0023 $$str
         |   |'''.stripMargin
         |raw'''prefix \\u0023 \\u0024 \\u0025 $$str \\\\u0023 $$str
         |     |\\u0023 \\u0024 \\u0025 $$str \\\\u0023 $$str suffix
         |     |\\u0023 \\u0024 \\u0025 $$str \\\\u0023 $$str
         |     |'''.stripMargin
         |""".stripMargin.fixTripleQuotes,
      s"""val str = " text"
         |
         |("prefix # $$ % " + str + " \\\\u0023 " + str + "\\n# $$ % " + str + " \\\\u0023 " + str + " suffix\\n# $$ % " + str + " \\\\u0023 " + str + "\\n").stripMargin
         |("prefix # $$ % " + str + " \\\\u0023 " + str + "\\n# $$ % " + str + " \\\\u0023 " + str + " suffix\\n# $$ % " + str + " \\\\u0023 " + str + "\\n").stripMargin
         |("prefix # $$ % " + str + " \\\\\\\\u0023 " + str + "\\n# $$ % " + str + " \\\\\\\\u0023 " + str + " suffix\\n# $$ % " + str + " \\\\\\\\u0023 " + str + "\\n").stripMargin
         |""".stripMargin
    )

  // NOTE: injected elements can contain margins, so we can't simply remove enclosing strip margin
  def testFromMultilineStringStripMarginCallShouldRemainAfterConversion(): Unit =
    doBulkTest(
      """val str = "\n|inner1\n|inner2"
        |s'''$str
        |   |$str
        |   |$str'''.stripMargin
        |""".stripMargin.fixTripleQuotes,
      """val str = "\n|inner1\n|inner2"
        |(str + "\n" + str + "\n" + str).stripMargin
        |""".stripMargin
    )
}