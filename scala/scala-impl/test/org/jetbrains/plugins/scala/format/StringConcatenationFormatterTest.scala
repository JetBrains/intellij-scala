package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.junit.Assert._

class StringConcatenationFormatterTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testEmpty(): Unit = {
    assertEquals("\"\"", format())
  }

  def testText(): Unit = {
    assertEquals(""""foo"""", format(Text("foo")))
  }

  def testText_SiblingsShouldBeMerged(): Unit = {
    assertEquals(""""aabb"""", format(Text("aa"), Text("bb")))
    assertEquals(""""aabbccc"""", format(Text("aa"), Text("bb"), Text("ccc")))
    assertEquals(""""aabbccc"""", format(Text("aa"), Text("bb"), Text("ccc")))
  }

  def testTextLike_SiblingsShouldBeMerged(): Unit = {
    assertEquals(""""% text %"""",
      format(SpecialFormatEscape.PercentChar, Text(" text "), SpecialFormatEscape.PercentChar))
    assertEquals(""""\n text \n"""",
      format(SpecialFormatEscape.LineSeparator, Text(" text "), SpecialFormatEscape.LineSeparator))
    assertEquals(""""% text \n"""",
      format(SpecialFormatEscape.PercentChar, Text(" text "), SpecialFormatEscape.LineSeparator))
    assertEquals(""""\n text %"""",
      format(SpecialFormatEscape.LineSeparator, Text(" text "), SpecialFormatEscape.PercentChar))
  }

  def testTextLike_SiblingsShouldBeMerged_1(): Unit = {
    assertEquals(s""""%% text %"""", format(
      SpecialFormatEscape.PercentChar,
      SpecialFormatEscape.PercentChar,
      Text(" text "),
      SpecialFormatEscape.PercentChar,
    ))

    assertEquals(s""""%% text %" + int""", format(
      SpecialFormatEscape.PercentChar,
      SpecialFormatEscape.PercentChar,
      Text(" text "),
      SpecialFormatEscape.PercentChar,
      Injection(exp("int"), None),
    ))

    assertEquals(s"""int.toString + "%% text %"""", format(
      Injection(exp("int"), None),
      SpecialFormatEscape.PercentChar,
      SpecialFormatEscape.PercentChar,
      Text(" text "),
      SpecialFormatEscape.PercentChar,
    ))

    assertEquals(s"""int.toString + "%%" + str + " text %"""", format(
      Injection(exp("int"), None),
      SpecialFormatEscape.PercentChar,
      SpecialFormatEscape.PercentChar,
      Injection(exp("str"), None),
      Text(" text "),
      SpecialFormatEscape.PercentChar,
    ))
  }

  def testEscapeChar(): Unit = {
    assertEquals(""""\n"""", format(Text("\n")))
    assertEquals(""""\t\r\n"""", format(Text("\t\r\n")))
  }

  def testEscapeChar_WithUnicodeCandidate(): Unit = {
    assertEquals("\"\\\\u0025\"", format(Text("\\u0025")))
  }

  def testSlash(): Unit = {
    assertEquals(""""\\"""", format(Text("\\")))
    assertEquals(""""\\\\"""", format(Text("\\\\")))
    assertEquals(""""\\ \\"""", format(Text("\\ \\")))
    assertEquals("""" \\ \\\\ \\\\ """", format(Text(" \\ \\\\ \\\\ ")))
  }

  def testPlainExpression_String(): Unit = {
    assertEquals("""str""", format(Injection(exp("""str"""), None)))
  }

  def testPlainExpression_Numeric(): Unit = {
    assertEquals("""int.toString""", format(Injection(exp("int"), None)))
    assertEquals("""long.toString""", format(Injection(exp("long"), None)))
    assertEquals("""bool.toString""", format(Injection(exp("bool"), None)))
  }

  def testExpressionWithDispensableFormat(): Unit = {
    assertEquals("""str""", format(Injection(exp("str"), Some(Specifier(null, "%s")))))
    assertEquals("""int.toString""", format(Injection(exp("int"), Some(Specifier(null, "%d")))))
  }

  def testExpressionWithMandatoryFormat(): Unit = {
    assertEquals("""int.formatted("%2d")""", format(Injection(exp("int"), Some(Specifier(null, "%2d")))))
  }

  def testPlainLiteral_Numeric(): Unit = {
    assertEquals("""123.toString""", format(Injection(exp("123"), None)))
    assertEquals("""123L.toString""", format(Injection(exp("123L"), None)))
    assertEquals("""true.toString""", format(Injection(exp("true"), None)))
  }

  def testPlainLiteral_String(): Unit = {
    assertEquals(""""text"""", format(Injection(exp(""""text""""), None)))
    assertEquals("""s"text"""", format(Injection(exp("""s"text""""), None)))
    assertEquals("""f"text"""", format(Injection(exp("""f"text""""), None)))
  }

  //noinspection RedundantBlock
  def testPlainLiteral_MultilineString(): Unit = {
    val qqq = "\"\"\""
    assertEquals(s"""${qqq}text${qqq}""", format(Injection(exp(s"""${qqq}text${qqq}"""), None)))
    assertEquals(s"""s${qqq}text${qqq}""", format(Injection(exp(s"""s${qqq}text${qqq}"""), None)))
    assertEquals(s"""f${qqq}text${qqq}""", format(Injection(exp(s"""f${qqq}text${qqq}"""), None)))
  }

  def testMultipleLiterals_Mixed(): Unit = {
    assertEquals(""""text" + 123""", format(Injection(exp(""""text""""), None), Injection(exp("123"), None)))
    assertEquals("""123.toString + "text"""", format(Injection(exp("123"), None), Injection(exp(""""text""""), None)))

    assertEquals(""""text1" + "text2"""", format(Injection(exp(""""text1""""), None), Injection(exp(""""text2""""), None)))
    assertEquals("""123.toString + 456L""", format(Injection(exp("123"), None), Injection(exp("456L"), None)))
  }

  def testMultipleExpressions_Mixed(): Unit = {
    assertEquals("""str + int""", format(Injection(exp("str"), None), Injection(exp("int"), None)))
    assertEquals("""123.toString + str""", format(Injection(exp("123"), None), Injection(exp("str"), None)))

    assertEquals("""str + str""", format(Injection(exp("str"), None), Injection(exp("str"), None)))
    assertEquals("""int.toString + long""", format(Injection(exp("int"), None), Injection(exp("long"), None)))

    assertEquals(""""foo " + str + " bar"""", format(Text("foo "), Injection(exp("str"), None), Text(" bar")))
    assertEquals(""""foo " + int + " bar"""", format(Text("foo "), Injection(exp("int"), None), Text(" bar")))
  }

  def testLiteralWithDispensableFormat(): Unit = {
    assertEquals("""123.toString""", format(Injection(exp("123"), Some(Specifier(null, "%d")))))
  }

  def testLiteralWithMandatoryFormat(): Unit = {
    assertEquals("123.formatted(\"%2d\")", format(Injection(exp("123"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexExpression(): Unit = {
    assertEquals("obj.str", format(Injection(exp("obj.str"), None)))
  }

  def testComplexExpressionWithDispensableFormat(): Unit = {
    assertEquals("obj.str", format(Injection(exp("obj.str"), Some(Specifier(null, "%d")))))
  }

  def testComplexExpressionWithMandatoryFormat(): Unit = {
    assertEquals("obj.str.formatted(\"%2d\")", format(Injection(exp("obj.str"), Some(Specifier(null, "%2d")))))
  }

  def testPlainBlockExpression(): Unit = {
    assertEquals("obj.str", format(Injection(exp("{obj.str}"), None)))
  }
  def testPlainBlockExpression_1(): Unit = {
    assertEquals("obj.strCall()", format(Injection(exp("{obj.strCall()}"), None)))
  }
  def testReference(): Unit = {
    assertEquals("obj.str", format(Injection(exp("obj.str"), None)))
  }
  def testMethodCall(): Unit = {
    assertEquals("obj.strCall()", format(Injection(exp("obj.strCall()"), None)))
  }

  def testBlockExpressionWithDispensableFormat(): Unit = {
    assertEquals("obj.str", format(Injection(exp("{obj.str}"), Some(Specifier(null, "%d")))))
  }

  def testBlockExpressionWithMandatoryFormat(): Unit = {
    assertEquals("obj.str.formatted(\"%2d\")", format(Injection(exp("{obj.str}"), Some(Specifier(null, "%2d")))))
  }

  def testPlainComplexBlockExpression(): Unit = {
    assertEquals("{null; obj.str}", format(Injection(exp("{null; obj.str}"), None)))
  }

  def testComplexBlockExpressionWithDispensableFormat(): Unit = {
    assertEquals("{null; obj.str}", format(Injection(exp("{null; obj.str}"), Some(Specifier(null, "%d")))))
  }

  def testComplexBlockExpressionWithMandatoryFormat(): Unit = {
    assertEquals("{null; obj.str}.formatted(\"%2d\")", format(Injection(exp("{null; obj.str}"), Some(Specifier(null, "%2d")))))
  }

  def testOther(): Unit = {
    assertEquals("", format(UnboundExpression(exp("foo"))))
  }

  private def format(parts: StringPart*): String = {
    StringConcatenationFormatter.format(parts)
  }

  private lazy val contextFile: ScalaFile = ScalaPsiElementFactory.createScalaFileFromText(
    """class A {
      |  def obj: A        = new A
      |  def str: String   = "text"
      |  def strCall(): String   = "text"
      |  def int: Int      = 42
      |  def long: Long    = 23L
      |  def bool: Boolean = true
      |}
      |""".stripMargin
  )(getProject)

  private def exp(s: String): ScExpression = {
    val context = contextFile.depthFirst().findByType[ScTemplateBody].get
    ScalaPsiElementFactory.createExpressionFromText(s, context)
  }
}
