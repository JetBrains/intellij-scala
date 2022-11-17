package org.jetbrains.plugins.scala.injection

import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.injection.AbstractLanguageInjectionTestCase.{ExpectedInjection, ShredInfo}
import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

class InterpolatedStringWithInjectionsMultilineTest extends AbstractLanguageInjectionTestCase {

  private def doInjectedViaCommentJsonTest(
    text: String,
    expectedText: String,
    expectedShreds: Seq[ShredInfo] = null
  ): Unit = {
    doInjectedViaCommentTest(JsonLangId, text, expectedText, expectedShreds)
  }

  private def doInjectedViaCommentTest(
    languageId: String,
    text: String,
    expectedText: String,
    expectedShreds: Seq[ShredInfo] = null
  ): Unit = {
    val textWithComment =
      s"""//language=$languageId
         |$text
         |""".stripMargin.replace("'''", "\"\"\"")
    val expectedInjection = ExpectedInjection(
      expectedText.withNormalizedSeparator,
      languageId,
      Option(expectedShreds)
    )
    doTest(textWithComment, expectedInjection)
  }

  def testEmptyString(): Unit =
    doInjectedViaCommentJsonTest(
      s"""s''''''.""",
      """""",
      Seq(ShredInfo((0, 0), (4, 4)))
    )

  def testOnlyText(): Unit =
    doInjectedViaCommentJsonTest(
      s"""s'''{
         |   |  "a" : 23,
         |   |  "b" : 42
         |   |}
         |   |'''.stripMargin""".stripMargin,
      """{
        |  "a" : 23,
        |  "b" : 42
        |}
        |""".stripMargin,
      Seq(
        ShredInfo((0, 2), (4, 6)),
        ShredInfo((2, 14), (6, 18)),
        ShredInfo((14, 25), (18, 29)),
        ShredInfo((25, 27), (29, 31)),
        ShredInfo((27, 27), (32, 32)),
      )
    )

  //With some plain content
  def testInjectionsInTheMiddle_Mix(): Unit =
    doInjectedViaCommentJsonTest(
      s"""s'''{
         |   |  "a" : $$value,
         |   |  "b" : $$value$${value}$$value,
         |   |  $$key : 1,
         |   |  $${key} : 2,
         |   |}
         |   |'''.stripMargin""".stripMargin,
      """{
        |  "a" : InjectionPlaceholder,
        |  "b" : InjectionPlaceholderInjectionPlaceholderInjectionPlaceholder,
        |  InjectionPlaceholder : 1,
        |  InjectionPlaceholder : 2,
        |}
        |""".stripMargin,
      Seq(
        ShredInfo((0, 2), (4, 6)),
        ShredInfo((2, 10), (6, 14)),
        ShredInfo((10, 32), (20, 22), "InjectionPlaceholder"),
        ShredInfo((32, 40), (22, 30)),
        ShredInfo((40, 60), (36, 36), "InjectionPlaceholder"),
        ShredInfo((60, 80), (44, 44), "InjectionPlaceholder"),
        ShredInfo((80, 102), (50, 52), "InjectionPlaceholder"),
        ShredInfo((102, 104), (52, 54)),
        ShredInfo((104, 130), (58, 64), "InjectionPlaceholder"),
        ShredInfo((130, 132), (64, 66)),
        ShredInfo((132, 158), (72, 78), "InjectionPlaceholder"),
        ShredInfo((158, 160), (78, 80)),
        ShredInfo((160, 160), (81, 81)),
      )
    )

  def testLeadingAndTrailingInjections(): Unit =
    doInjectedViaCommentJsonTest(
      s"""s'''$$START
         |   |  "a" : 23,
         |   |  "b" : 42
         |   |$${END}'''.stripMargin""".stripMargin,
      """InjectionPlaceholder
        |  "a" : 23,
        |  "b" : 42
        |InjectionPlaceholder""".stripMargin,
      Seq(
        ShredInfo((0, 0), (4, 4)),
        ShredInfo((0, 21), (10, 11), "InjectionPlaceholder"),
        ShredInfo((21, 33), (11, 23)),
        ShredInfo((33, 44), (23, 34)),
        ShredInfo((44, 44), (35, 35)),
        ShredInfo((44, 64), (40, 40), "InjectionPlaceholder"),
      )
    )

  def testLeadingAndTrailingInjections_WithNewLineInTheEnd(): Unit =
    doInjectedViaCommentJsonTest(
      s"""s'''$$START
         |   |  "a" : 23,
         |   |  "b" : 42
         |   |$${END}
         |   |'''.stripMargin""".stripMargin,
      """InjectionPlaceholder
        |  "a" : 23,
        |  "b" : 42
        |InjectionPlaceholder
        |""".stripMargin,
      Seq(
        ShredInfo((0,0), (4,4), "", ""),
        ShredInfo((0,21), (10,11), "InjectionPlaceholder", ""),
        ShredInfo((21,33), (11,23), "", ""),
        ShredInfo((33,44), (23,34), "", ""),
        ShredInfo((44,44), (35,35), "", ""),
        ShredInfo((44,65), (40,41), "InjectionPlaceholder", ""),
        ShredInfo((65,65), (42,42), "", ""),
      )
    )

  def testRawInterpolatorWithEscapeSequences(): Unit = {
    doInjectedViaCommentTest(
      RegexpLangId,
      raw"""//language=RegExp
           |raw'''\w
           |     |\w\w
           |     |\w $$foo \w
           |     |$$foo \w $$foo
           |     |\w'''.stripMargin""".stripMargin,
      """\w
        |\w\w
        |\w InjectionPlaceholder \w
        |InjectionPlaceholder \w InjectionPlaceholder
        |\w""".stripMargin,
      Seq(
        ShredInfo((0, 3), (6, 9)),
        ShredInfo((3, 8), (9, 14)),
        ShredInfo((8, 11), (14, 17)),
        ShredInfo((11, 35), (21, 25), "InjectionPlaceholder"),
        ShredInfo((35, 35), (26, 26)),
        ShredInfo((35, 59), (29, 33), "InjectionPlaceholder"),
        ShredInfo((59, 80), (37, 38), "InjectionPlaceholder"),
        ShredInfo((80, 82), (38, 40)),
      )
    )
  }

  //Some edge cases when there string consists from injections only
  def testOnlyInjections_Single(): Unit =
    doInjectedViaCommentJsonTest(
      """s'''$foo'''.r""",
      "InjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (4, 4)),
        ShredInfo((0, 20), (8, 8), "InjectionPlaceholder"),
      )
    )

  def testOnlyInjections_SingleWithBraces(): Unit =
    doInjectedViaCommentJsonTest(
      """s'''${foo}'''.r""",
      "InjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (4, 4)),
        ShredInfo((0, 20), (10, 10), "InjectionPlaceholder"),
      )
    )

  def testOnlyInjections_TwoSiblings_1(): Unit =
    doInjectedViaCommentJsonTest(
      """s'''$foo${foo}'''.r""",
      "InjectionPlaceholderInjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (4, 4)),
        ShredInfo((0, 20), (8, 8), "InjectionPlaceholder"),
        ShredInfo((20, 40), (14, 14), "InjectionPlaceholder"),
      )
    )

  def testOnlyInjections_TwoSiblings_2(): Unit =
    doInjectedViaCommentJsonTest(
      """s'''${foo}$foo'''.r""",
      "InjectionPlaceholderInjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (4, 4)),
        ShredInfo((0, 20), (10, 10), "InjectionPlaceholder"),
        ShredInfo((20, 40), (14, 14), "InjectionPlaceholder"),
      )
    )
}
