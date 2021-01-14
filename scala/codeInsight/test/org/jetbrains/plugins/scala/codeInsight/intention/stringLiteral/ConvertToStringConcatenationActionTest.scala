package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProfileManager
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.codeInsight.intentions
import org.jetbrains.plugins.scala.codeInspection.parentheses.{ScalaUnnecessaryParenthesesInspection, UnnecessaryParenthesesSettings}
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ConvertToStringConcatenationActionTest extends intentions.ScalaIntentionTestBase {

  override def familyName: String = FormatConversionIntention.ConvertToStringConcat

  override def setUp(): Unit = {
    super.setUp()
    // we apply ScalaUnnecessaryParenthesesInspection when converting to concatenation
    getFixture.enableInspections(classOf[ScalaUnnecessaryParenthesesInspection])
  }

  override protected def tearDown(): Unit =
    super.tearDown()

  def testFromInterpolated(): Unit = {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two"
         |}
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET"one " + x + " two"
         |}
         |"""

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
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET("one " + x + " two").length
         |}
         |"""

    doTest(before, after)
  }

  def testFromInterpolated_WrapWholeConcatWithBraces_WithMethodCall2(): Unit = {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two".substring(23)
         |}
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET("one " + x + " two").substring(23)
         |}
         |"""

    doTest(before, after)
  }

  def testFromInterpolated_WrapWholeConcatWithBraces_WithPostfixMethodCall(): Unit = {
    val before =
      s"""object A {
         |  val x = 42
         |  ${CARET}s"one $$x two" length
         |}
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  $CARET("one " + x + " two") length
         |}
         |"""

    doTest(before, after)
  }

  def testFromInterpolated_WrapWholeConcatWithBraces_WithInfixMethodCall(): Unit = {
    val before =
      s"""object A {
         |  val x = 42
         |  obj foo ${CARET}s"one $$x two"
         |}
         |"""

    val after =
      s"""object A {
         |  val x = 42
         |  obj foo $CARET("one " + x + " two")
         |}
         |"""

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
        |().toString + () + " b"""".stripMargin
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

  // NOTE: current implementation only works when each intention action does not adds new lines or removes some lines
  protected def doBulkTest(
    text: String,
    resultText: String,
    fileType: FileType = fileType
  ): Unit = {
    implicit val project: Project = getProject

    getFixture.configureByText(fileType, normalize(text)).asInstanceOf[ScalaFile]

    placeCaretAtEachLineContent(getEditor)

    val caretModel  = getEditor.getCaretModel

    val carets = caretModel.getAllCarets.asScala.toSeq.map(_.getVisualPosition)
    carets.foreach { caret =>
      caretModel.getCurrentCaret.moveToVisualPosition(caret)

      val intention = findIntentionByName(familyName)
      intention.foreach { action =>
        executeWriteActionCommand("Invoke Intention Action") {
          action.invoke(project, getEditor, getFile)
        }
      }
    }

    checkIntentionResultText(resultText)(text)
  }

  /**
   * {{{
   * class A {
   *   2 + 2
   * }
   * }}}
   * ->
   * {{{
   * <caret>class A {
   *   <caret>2 + 2
   * <caret>}
   * }}}
   */
  private def placeCaretAtEachLineContent(editor: Editor): Unit = {
    val document = editor.getDocument
    val caretModel = editor.getCaretModel
    val text = document.getText

    // place a caret at the beginning of content on each line
    (0 until document.getLineCount).foreach { line =>
      var contentOnLineOffset = document.getLineStartOffset(line)
      while (text.charAt(contentOnLineOffset).isWhitespace)
        contentOnLineOffset += 1

      caretModel.addCaret(editor.offsetToVisualPosition(contentOnLineOffset))
    }
  }
}
