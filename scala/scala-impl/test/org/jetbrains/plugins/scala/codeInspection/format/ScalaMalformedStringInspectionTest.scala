package org.jetbrains.plugins.scala.codeInspection.format

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.findCaretOffset

import scala.collection.JavaConverters
import scala.util.Try

class ScalaMalformedStringInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {

  protected override def setUp(): Unit = {
    super.setUp()
    getFixture.enableInspections(classOf[ScalaMalformedFormatStringInspection])
  }

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

    import JavaConverters._
    fixture.doHighlighting()
      .asScala
      .flatMap(info => Option(info.getDescription))
      .filter(_.contains(" cannot be used for a"))
  }

  def test_all(): Unit = {
    for (specifier <- formatSpecifiers; (arg, repr) <- arguments) {
      val shouldHaveInspection = Try(specifier.format(repr)).isFailure

      val inspections = findInspections(
        s"""String.format("$specifier", $arg)"""
      )
      val inspectionsList = inspections.mkString(", ")

      if (shouldHaveInspection) {
        val ty = repr.getClass.getSimpleName

        val hintForSpecifier = s"Format specifier $specifier cannot be used for an argument $arg (---)"
        val inspectionsWithoutTypes = inspections.map(" \\(.*\\)".r.replaceFirstIn(_, " (---)"))
        assert(inspectionsWithoutTypes.contains(hintForSpecifier), s"Expected to find hint: $hintForSpecifier (found: $inspectionsList)")

        val hintForArg = s"Argument $arg (---) cannot be used for a format specifier $specifier"
        assert(inspectionsWithoutTypes.contains(hintForArg), s"Expected to find hint: $hintForArg (found: $inspectionsList)")
      } else {
        assert(inspections.isEmpty, s"Found: $inspectionsList")
      }
    }
  }
}
