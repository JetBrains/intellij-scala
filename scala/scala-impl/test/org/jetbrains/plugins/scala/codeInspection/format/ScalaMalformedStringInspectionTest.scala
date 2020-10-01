package org.jetbrains.plugins.scala.codeInspection.format

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.findCaretOffset
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._
import scala.util.Try

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
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
    "%.3f",
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
    "StringBuilder.newBuilder" -> new StringBuilder(),
    "new java.lang.StringBuilder()" -> new java.lang.StringBuilder(),
    "BigInt(0)" -> BigInt(0),
    "BigDecimal(0)" -> BigDecimal(0),
    "new java.math.BigInteger(\"0\")" -> new java.math.BigInteger("0"),
    "java.util.Calendar.getInstance" -> java.util.Calendar.getInstance,
    "new java.util.Date" -> new java.util.Date(),
    "new java.lang.Boolean(true)" -> java.lang.Boolean.valueOf(true),
    "new java.lang.Byte(0.toByte)" -> java.lang.Byte.valueOf(0.toByte),
    "new java.lang.Character('c')" -> java.lang.Character.valueOf('c'),
    "new java.lang.Short(0.toShort)" -> java.lang.Short.valueOf(0.toShort),
    "new java.lang.Integer(0)" -> java.lang.Integer.valueOf(0),
    "new java.lang.Long(0L)" -> java.lang.Long.valueOf(0L),
    "new java.lang.Float(0.0)" -> java.lang.Float.valueOf(0.0F),
    "new java.lang.Double(0D)" -> java.lang.Double.valueOf(0D),
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

  def build_test(codeFromSpecifierAndArg: (String, String) => String): (String, Seq[String]) = {
    val codeBuilder = new StringBuilder()
    val testBuilder = Seq.newBuilder[String]

    for (specifier <- formatSpecifiers; (arg, repr) <- arguments) {
      codeBuilder ++= codeFromSpecifierAndArg(specifier, arg)
      codeBuilder += '\n'

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

  def run_all(codeFromSpecifierAndArg: (String, String) => String): Unit = {
    val (code, expectedHints) = build_test(codeFromSpecifierAndArg)
    assert(expectedHints.length >= 100, "There must be something wrong with the test building...")
    assert(expectedHints.distinct.length == expectedHints.length)

    val foundHints = findInspections(code)
    val foundHintsWithoutTypes = foundHints.map(" \\(.*\\)".r.replaceFirstIn(_, " (---)"))

    for (expected <- expectedHints) {
      assert(foundHintsWithoutTypes.contains(expected), s"Couldn't find: $expected")
    }

    for (found <- foundHintsWithoutTypes) {
      assert(expectedHints.contains(found), s"Didn't expected: $found")
    }
  }

  def test_format_call(): Unit =
    run_all((specifier, arg) => s"""String.format("$specifier", $arg)""")

  def test_interpolated(): Unit =
    run_all((specifier, arg) => s"""f"$${$arg}$specifier"""")

  def test_printf(): Unit =
    run_all((specifier, arg) => s"""printf("$specifier", $arg)""")
}
