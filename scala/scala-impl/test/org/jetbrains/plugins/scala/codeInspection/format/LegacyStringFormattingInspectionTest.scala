package org.jetbrains.plugins.scala.codeInspection.format

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class LegacyStringFormattingInspectionTest extends ScalaInspectionTestBase {

  override protected val description = "Legacy string formatting, an interpolated string can be used instead"

  override protected val classOfInspection: Class[LegacyStringFormattingInspection] =
    classOf[LegacyStringFormattingInspection]

  val hint = "Convert to interpolated string"

  def test_string_int_concat(): Unit = {
    checkTextHasError(START + "\"test\" + 3" + END)

    testQuickFix(
      "\"test\" + 3",
      "\"test3\"",
      hint
    )
  }

  def test_string_string_concat(): Unit = {
    testQuickFix(
      "\"test\" + \"abc\"",
      "\"testabc\"",
      hint
    )
  }

  def test_int_string_concat(): Unit = {
    testQuickFix(
      "2 + \"abc\"",
      "\"2abc\"",
      hint
    )
  }

  def test_string_ref_concat(): Unit = {
    testQuickFix(
      raw"""
           |val x = 3
           |"abc" + x
           |""".stripMargin,
      raw"""
           |val x = 3
           |s"abc$$x"
           |""".stripMargin,
      hint
    )
  }

  def test_ref_string_concat(): Unit = {
    testQuickFix(
      raw"""
           |val x = 3
           |x + "abc"
           |""".stripMargin,
      raw"""
           |val x = 3
           |s"$${x}abc"
           |""".stripMargin,
      hint
    )
  }

  def test_formatted_string(): Unit = {
    testQuickFix(
      "123.formatted(\"test\")",
      "\"test\"",
      hint
    )
  }

  def test_formatted_ref(): Unit = {
    testQuickFix(
      raw"""
           |val ref = 3
           |ref.formatted("abc %s")
           |""".stripMargin,
      raw"""
           |val ref = 3
           |s"abc $$ref"
           |""".stripMargin,
      hint
    )
  }

  def test_format(): Unit = {
    testQuickFix(
      "\"%s xyz %s\".format(\"test\", 3)",
      "\"test xyz 3\"",
      hint
    )
  }

  def test_combination(): Unit = {
    // check that there is really only one inspection and not multiple on the individual parts
    checkTextHasError(START + "\"a\" + \"b\" + \" %s %s \".format(1, 2) + \"c\"" + END)

    testQuickFix(
      "\"a\" + \"b\" + \" %s %s \".format(1, 2) + \"c\"",
      "\"ab 1 2 c\"",
      hint
    )
  }

  def test_string(): Unit =
    checkTextHasNoErrors("\"blub\"")
}
