package org.jetbrains.plugins.scala.injection

import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.injection.ScalaInjectionTestFixture.{ExpectedInjection, ShredInfo}
import org.jetbrains.plugins.scala.injection.InjectionTestUtils.{JsonLangId, RegexpLangId}
import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

class InterpolatedStringWithInjectionsTest extends ScalaLanguageInjectionTestBase {

  private def doInjectedRegexpTest(
    text: String,
    expectedText: String,
    expectedShreds: Seq[ShredInfo] = null
  ): Unit = {
    val expectedInjection = ExpectedInjection(
      expectedText.withNormalizedSeparator,
      RegexpLangId,
      Option(expectedShreds)
    )
    scalaInjectionTestFixture.doTest(text, expectedInjection)
  }

  def testEmptyString(): Unit =
    doInjectedRegexpTest(
      """s"".r""",
      """""",
      Seq(ShredInfo((0, 0), (2, 2)))
    )

  def testOnlyText(): Unit =
    scalaInjectionTestFixture.doTest(
      """s"abc".r""",
      ExpectedInjection(
        """abc""",
        RegexpLangId,
        shreds = Some(Seq(ShredInfo((0, 3), (2, 5)))),
        isUnparseable = Some(null)
      )
    )

  //With some plain content
  def testInjectionsInTheMiddle(): Unit = {
    scalaInjectionTestFixture.doTest(
      """s"aaa $foo bbb $foo ccc".r""",
      ExpectedInjection(
        """aaa InjectionPlaceholder bbb InjectionPlaceholder ccc""",
        RegexpLangId,
        shreds = Some(Seq(
          ShredInfo((0, 4), (2, 6)),
          ShredInfo((4, 29), (10, 15), "InjectionPlaceholder"),
          ShredInfo((29, 53), (19, 23), "InjectionPlaceholder"),
        )),
        isUnparseable = Some(true)
      )
    )
  }

  def testInjectionsInTheMiddle_WithBraces(): Unit =
    doInjectedRegexpTest(
      """s"aaa ${foo} bbb ${foo} ccc".r""",
      """aaa InjectionPlaceholder bbb InjectionPlaceholder ccc""",
      Seq(
        ShredInfo((0, 4), (2, 6)),
        ShredInfo((4, 29), (12, 17), "InjectionPlaceholder"),
        ShredInfo((29, 53), (23, 27), "InjectionPlaceholder"),
      )
    )

  def testInjectionsInTheMiddle_MultipleMixed(): Unit =
    doInjectedRegexpTest(
      """s"aaa ${foo}${foo}${foo} bbb $foo$foo$foo ccc".r""",
      """aaa InjectionPlaceholderInjectionPlaceholderInjectionPlaceholder bbb InjectionPlaceholderInjectionPlaceholderInjectionPlaceholder ccc""",
      Seq(
        ShredInfo((0, 4), (2, 6)),
        ShredInfo((4, 24), (12, 12), "InjectionPlaceholder"),
        ShredInfo((24, 44), (18, 18), "InjectionPlaceholder"),
        ShredInfo((44, 69), (24, 29), "InjectionPlaceholder"),
        ShredInfo((69, 89), (33, 33), "InjectionPlaceholder"),
        ShredInfo((89, 109), (37, 37), "InjectionPlaceholder"),
        ShredInfo((109, 133), (41, 45), "InjectionPlaceholder"),
      )
    )

  def testLeadingAndTrailingInjections(): Unit =
    doInjectedRegexpTest(
      """s"$foo aaa $foo".r""",
      """InjectionPlaceholder aaa InjectionPlaceholder""",
      Seq(
        ShredInfo((0, 0), (2, 2)),
        ShredInfo((0, 25), (6, 11), "InjectionPlaceholder"),
        ShredInfo((25, 45), (15, 15), "InjectionPlaceholder"),
      )
    )

  def testLeadingAndTrailingInjections_WithBraces(): Unit =
    doInjectedRegexpTest(
      """s"${foo} aaa ${foo}".r""",
      """InjectionPlaceholder aaa InjectionPlaceholder""",
      Seq(
        ShredInfo((0, 0), (2, 2)),
        ShredInfo((0, 25), (8, 13), "InjectionPlaceholder"),
        ShredInfo((25, 45), (19, 19), "InjectionPlaceholder"),
      )
    )

  def testLeadingAndTrailingInjections_MultipleMixed(): Unit =
    doInjectedRegexpTest(
      """s"${foo}${foo}${foo} aaa $foo$foo$foo".r""",
      """InjectionPlaceholderInjectionPlaceholderInjectionPlaceholder aaa InjectionPlaceholderInjectionPlaceholderInjectionPlaceholder""",
      Seq(
        ShredInfo((0, 0), (2, 2)),
        ShredInfo((0, 20), (8, 8), "InjectionPlaceholder"),
        ShredInfo((20, 40), (14, 14), "InjectionPlaceholder"),
        ShredInfo((40, 65), (20, 25), "InjectionPlaceholder"),
        ShredInfo((65, 85), (29, 29), "InjectionPlaceholder"),
        ShredInfo((85, 105), (33, 33), "InjectionPlaceholder"),
        ShredInfo((105, 125), (37, 37), "InjectionPlaceholder"),
      )
    )

  def testRawInterpolatorWithEscapeSequences(): Unit = {
    doInjectedRegexpTest(
      s"""raw"aaa \\w\\s $${foo + foo} \\w\\s bbb \\w\\s $$foo \\w\\s ccc \\w\\s".r""".stripMargin,
      """aaa \w\s InjectionPlaceholder \w\s bbb \w\s InjectionPlaceholder \w\s ccc \w\s""",
      Seq(
        ShredInfo((0, 9), (4, 13)),
        ShredInfo((9, 44), (25, 40), "InjectionPlaceholder"),
        ShredInfo((44, 78), (44, 58), "InjectionPlaceholder"),
      )
    )
  }

  //Some edge cases when the string consists from injections only
  def testOnlyInjections_Single(): Unit =
    doInjectedRegexpTest(
      """s"$foo".r""",
      "InjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (2, 2)),
        ShredInfo((0, 20), (6, 6), "InjectionPlaceholder"),
      )
    )

  def testOnlyInjections_SingleWithBraces(): Unit =
    doInjectedRegexpTest(
      """s"${foo}".r""",
      "InjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (2, 2)),
        ShredInfo((0, 20), (8, 8), "InjectionPlaceholder"),
      )
    )

  def testOnlyInjections_TwoSiblings_1(): Unit =
    doInjectedRegexpTest(
      """s"$foo${foo}".r""",
      "InjectionPlaceholderInjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (2, 2)),
        ShredInfo((0, 20), (6, 6), "InjectionPlaceholder"),
        ShredInfo((20, 40), (12, 12), "InjectionPlaceholder"),
      )
    )

  def testOnlyInjections_TwoSiblings_2(): Unit =
    doInjectedRegexpTest(
      """s"${foo}$foo".r""",
      "InjectionPlaceholderInjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (2, 2)),
        ShredInfo((0, 20), (8, 8), "InjectionPlaceholder"),
        ShredInfo((20, 40), (12, 12), "InjectionPlaceholder"),
      )
    )

  def testOnlyInjections_TwoSiblings_3(): Unit =
    doInjectedRegexpTest(
      """s"${foo}${foo}".r""",
      "InjectionPlaceholderInjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (2, 2)),
        ShredInfo((0, 20), (8, 8), "InjectionPlaceholder"),
        ShredInfo((20, 40), (14, 14), "InjectionPlaceholder"),
      )
    )

  def testOnlyInjections_ThreeSiblings_(): Unit =
    doInjectedRegexpTest(
      """s"${foo}$foo${foo}".r""".stripMargin,
      "InjectionPlaceholderInjectionPlaceholderInjectionPlaceholder",
      Seq(
        ShredInfo((0, 0), (2, 2)),
        ShredInfo((0, 20), (8, 8), "InjectionPlaceholder"),
        ShredInfo((20, 40), (12, 12), "InjectionPlaceholder"),
        ShredInfo((40, 60), (18, 18), "InjectionPlaceholder"),
      )
    )

  //
  //Different ways of injections
  //
  def testInjectionViaComment(): Unit =
    scalaInjectionTestFixture.doTest(
      """//language=JSON
        |s"hello ${foo} world $foo !"""".stripMargin,
      ExpectedInjection(
        "hello InjectionPlaceholder world InjectionPlaceholder !",
        JsonLangId,
        Some(Seq(
          ShredInfo((0, 6), (2, 8)),
          ShredInfo((6, 33), (14, 21), "InjectionPlaceholder"),
          ShredInfo((33, 55), (25, 27), "InjectionPlaceholder"),
        ))
      )
    )

  def testInjectionViaAnnotation(): Unit =
    scalaInjectionTestFixture.doTest(
      s"""class Language(val value: String) extends scala.annotation.StaticAnnotation
         |
         |class A {
         |  def foo(@Language("JSON") param: String): Unit = ???
         |  foo(s"hello $${foo} ${CARET}world $$foo !")
         |}
         |""".stripMargin,
      ExpectedInjection(
        "hello InjectionPlaceholder world InjectionPlaceholder !",
        JsonLangId,
        Some(Seq(
          ShredInfo((0, 6), (2, 8)),
          ShredInfo((6, 33), (14, 21), "InjectionPlaceholder"),
          ShredInfo((33, 55), (25, 27), "InjectionPlaceholder"),
        ))
      )
    )

  def testInjectionViaStringInterpolator(): Unit =
    scalaInjectionTestFixture.doTest(
      s"""json"hello $${foo} ${CARET}world $$foo !"
         |
         |implicit class StringContextOps(val sc: StringContext) {
         |  def json(args: Any*): String = ???
         |}
         |s""".stripMargin,
      ExpectedInjection(
        "hello InjectionPlaceholder world InjectionPlaceholder !",
        JsonLangId,
        Some(Seq(
          ShredInfo((0, 6), (5, 11)),
          ShredInfo((6, 33), (17, 24), "InjectionPlaceholder"),
          ShredInfo((33, 55), (28, 30), "InjectionPlaceholder"),
        ))
      )
    )

  //
  // Other
  //

  def testConcatenationOfInterpolatedStrings(): Unit =
    scalaInjectionTestFixture.doTest(
      s"""object Wrapper {
         |  val foo = 42
         |  //language=RegExp prefix=myPrefix suffix=mySuffix
         |  s"${CARET}aaa $$foo" +
         |    s" bbb $${foo} ccc " +
         |    s"$${foo} ddd"
         |}""".stripMargin,
      ExpectedInjection(
        "myPrefixaaa InjectionPlaceholder bbb InjectionPlaceholder ccc InjectionPlaceholder dddmySuffix",
        RegexpLangId,
        Some(Seq(
          ShredInfo((0, 0), (0, 0)),
        ))
      )
    )


  //
  // Dollar sign escape: $$
  //
  def testEscapedDollarSign_OnlyDollar(): Unit = {
    doInjectedRegexpTest(
      """raw"$$ $$ $$".r""".stripMargin,
      """InjectionPlaceholder InjectionPlaceholder InjectionPlaceholder""",
      Seq(
        ShredInfo((0, 0), (4, 4)),
        ShredInfo((0, 21), (6, 7), "InjectionPlaceholder"),
        ShredInfo((21, 42), (9, 10), "InjectionPlaceholder"),
        ShredInfo((42, 62), (12, 12), "InjectionPlaceholder"),
      )
    )
  }

  def testEscapedDollarSign_ManyDollars(): Unit = {
    doInjectedRegexpTest(
      """raw"$$$$ $$$$ $$$$".r""".stripMargin,
      """InjectionPlaceholderInjectionPlaceholder InjectionPlaceholderInjectionPlaceholder InjectionPlaceholderInjectionPlaceholder""",
      Seq(
        ShredInfo((0, 0), (4, 4)),
        ShredInfo((0, 20), (6, 6), "InjectionPlaceholder"),
        ShredInfo((20, 41), (8, 9), "InjectionPlaceholder"),
        ShredInfo((41, 61), (11, 11), "InjectionPlaceholder"),
        ShredInfo((61, 82), (13, 14), "InjectionPlaceholder"),
        ShredInfo((82, 102), (16, 16), "InjectionPlaceholder"),
        ShredInfo((102, 122), (18, 18), "InjectionPlaceholder"),
      )
    )
  }

  def testEscapedDollarSign_WithText(): Unit = {
    doInjectedRegexpTest(
      """raw"start $$ middle $$ end".r""".stripMargin,
      """start InjectionPlaceholder middle InjectionPlaceholder end""",
      Seq(
        ShredInfo((0, 6), (4, 10)),
        ShredInfo((6, 34), (12, 20), "InjectionPlaceholder"),
        ShredInfo((34, 58), (22, 26), "InjectionPlaceholder"),
      )
    )
  }

  def testEscapedDollarSign_ManyDollarsWithText(): Unit = {
    doInjectedRegexpTest(
      """raw"start $$$$ middle $$$$ end".r""".stripMargin,
      """start InjectionPlaceholderInjectionPlaceholder middle InjectionPlaceholderInjectionPlaceholder end""",
      Seq(
        ShredInfo((0, 6), (4, 10)),
        ShredInfo((6, 26), (12, 12), "InjectionPlaceholder"),
        ShredInfo((26, 54), (14, 22), "InjectionPlaceholder"),
        ShredInfo((54, 74), (24, 24), "InjectionPlaceholder"),
        ShredInfo((74, 98), (26, 30), "InjectionPlaceholder"),
      )
    )
  }
}
