package org.jetbrains.plugins.scala.codeInspection.format

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.findCaretOffset
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

import scala.jdk.CollectionConverters._
import scala.util.Try

class ScalaMalformedStringInspectionTest extends ScalaInspectionTestBase {

  override protected val description = null // not used... better to throw a NPE
  override protected val classOfInspection: Class[ScalaMalformedFormatStringInspection] =
    classOf[ScalaMalformedFormatStringInspection]

  val formatSpecifiers = Seq(
    "%b",
    "%h",
    "%s",
    "%c",
    "%d",
    "%e",
    "%f",
    "%ts"
  )

  val arguments = Seq(
    "null" -> null,
    "true" -> true,
    "0.toByte" -> 0.toByte,
    "'c'" -> 'c',
    "0.toShort" -> 0.toShort,
    "0" -> 0,
    "0L" -> 0L,
    "0.0F" -> 0.0F,
    "0D" -> 0D,
    "\"\"" -> "",
    "\"\".asInstanceOf[CharSequence]" -> "".asInstanceOf[CharSequence],
    "StringBuilder.newBuilder" -> StringBuilder.newBuilder,
    "new java.lang.StringBuilder()" -> new java.lang.StringBuilder(),
    "BigInt(0)" -> BigInt(0),
    "BigDecimal(0)" -> BigDecimal(0),
    "new java.math.BigInteger(\"0\")" -> new java.math.BigInteger("0"),
    "java.util.Calendar.getInstance" -> java.util.Calendar.getInstance,
    "new java.util.Date" -> new java.util.Date(),
    "new java.lang.Boolean(true)" -> new java.lang.Boolean(true),
    "new java.lang.Byte(0.toByte)" -> new java.lang.Byte(0.toByte),
    "new java.lang.Character('c')" -> new java.lang.Character('c'),
    "new java.lang.Short(0.toShort)" -> new java.lang.Short(0.toShort),
    "new java.lang.Integer(0)" -> new java.lang.Integer(0),
    "new java.lang.Long(0L)" -> new java.lang.Long(0L),
    "new java.lang.Float(0.0)" -> new java.lang.Float(0.0),
    "new java.lang.Double(0D)" -> new java.lang.Double(0D),
    "new java.lang.Object" -> new java.lang.Object,
  )

  def findInspections(code: String): Seq[String] = {
    val (normalizedText, offset) = findCaretOffset(code, stripTrailingSpaces = true)

    val fixture = getFixture
    fixture.configureByText("dummy.scala", normalizedText)

    fixture.doHighlighting()
      .asScala
      .flatMap(info => Option(info.getDescription))
      .filter(_.contains(" cannot be used for a"))
      .toSeq
  }

  def build_test(): (String, Seq[String]) = {
    val codeBuilder = StringBuilder.newBuilder
    val testBuilder = Seq.newBuilder[String]

    for (specifier <- formatSpecifiers; (arg, repr) <- arguments) {
      codeBuilder ++= s"""String.format("$specifier", $arg)\n"""

      val shouldHaveInspection = Try(specifier.format(repr)).isFailure
      if (shouldHaveInspection) {
        val hintForSpecifier = s"Format specifier $specifier cannot be used for an argument $arg (---)"
        val hintForArg = s"Argument $arg (---) cannot be used for a format specifier $specifier"

        testBuilder += hintForSpecifier
        testBuilder += hintForArg
      }
    }

    (codeBuilder.result(), testBuilder.result())
  }

  def test_all(): Unit = {
    val (code, expectedHints) = build_test()
    assert(expectedHints.length >= 100, "There must be something wrong with the test building...")
    assert(expectedHints.distinct.length == expectedHints.length)

    val inspectionHints = findInspections(code)
    val inspectionHintsWithoutTypes = inspectionHints.map(" \\(.*\\)".r.replaceFirstIn(_, " (---)"))

    for(expected <- expectedHints) {
      assert(inspectionHintsWithoutTypes.contains(expected), s"Couldn't find: $expected")
    }
  }
}
