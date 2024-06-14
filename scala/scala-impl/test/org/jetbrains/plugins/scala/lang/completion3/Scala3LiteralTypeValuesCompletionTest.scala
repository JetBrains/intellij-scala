package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase

sealed class Scala3LiteralTypeValuesCompletionTest extends ScalaCompletionTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  protected def doOptimisticCompletionTest(fileText: String, resultText: String, item: String): Unit =
    doCompletionTest(fileText = fileText, resultText = resultText, item = item)

  def testUnionTypeVariable(): Unit = doOptimisticCompletionTest(
    fileText = s"val x: 42 | -1 = $CARET",
    resultText = s"val x: 42 | -1 = 42$CARET",
    item = "42",
  )

  def testUnionTypeVariable2(): Unit = doOptimisticCompletionTest(
    fileText = s"val x: 42 | -1 = $CARET",
    resultText = s"val x: 42 | -1 = -1$CARET",
    item = "-1",
  )

  def testUnionTypeFunction(): Unit = doOptimisticCompletionTest(
    fileText = s"def x(): 42 | -1 = $CARET",
    resultText = s"def x(): 42 | -1 = 42$CARET",
    item = "42",
  )

  def testUnionTypeFunctionBlock(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |def x(): 42 | -1 = {
         |  val y = 3
         |  println(y)
         |  $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def x(): 42 | -1 = {
         |  val y = 3
         |  println(y)
         |  42$CARET
         |}
         |""".stripMargin,
    item = "42",
  )

  def testSingleLiteralType(): Unit = doOptimisticCompletionTest(
    fileText = s"""val x: "literal_string_type" = $CARET""",
    resultText = s"""val x: "literal_string_type" = "literal_string_type"$CARET""",
    item = "\"literal_string_type\"",
  )

  def testUnionTypeAlias(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = $CARET
         |""".stripMargin,
    resultText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "green"$CARET
         |""".stripMargin,
    item = "\"green\"",
  )

  def testUnionTypeNestedAliases(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |type Color = "red" | GB
         |val color: Color = $CARET
         |""".stripMargin,
    resultText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |type Color = "red" | GB
         |val color: Color = "red"$CARET
         |""".stripMargin,
    item = "\"red\"",
  )

  def testUnionTypeNestedAliases2(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |type Color = "red" | GB
         |val color: Color = $CARET
         |""".stripMargin,
    resultText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |type Color = "red" | GB
         |val color: Color = "blue"$CARET
         |""".stripMargin,
    item = "\"blue\"",
  )

  def testUnionAndIntersectionTypeNestedAliases(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |val color: "red" | GB & Blue = $CARET
         |""".stripMargin,
    resultText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |val color: "red" | GB & Blue = "red"$CARET
         |""".stripMargin,
    item = "\"red\"",
  )

  def testUnionAndIntersectionTypeNestedAliases2(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |val color: "red" | GB & Blue = $CARET
         |""".stripMargin,
    item = "\"green\"",
  )

  // TODO(SCL-22620): this case should ideally be suggested because it would compile
  def testUnionAndIntersectionTypeNestedAliases3(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |val color: "red" | GB & Blue = $CARET
         |""".stripMargin,
    item = "\"blue\"",
  )

  def testIntersectionAndUnionTypeNestedAliases(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |val color: GB & Blue | "red" = $CARET
         |""".stripMargin,
    resultText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |val color: GB & Blue | "red" = "red"$CARET
         |""".stripMargin,
    item = "\"red\"",
  )

  def testIntersectionAndUnionTypeNestedAliases2(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |val color: GB & Blue | "red" = $CARET
         |""".stripMargin,
    item = "\"green\"",
  )

  // TODO(SCL-22620): this case should ideally be suggested because it would compile
  def testIntersectionAndUnionTypeNestedAliases3(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |type Blue = "blue"
         |type Green = "green"
         |type GB = Green | Blue
         |val color: GB & Blue | "red" = $CARET
         |""".stripMargin,
    item = "\"blue\"",
  )

  def testUnionTypeAliasInsideStringLiteral(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "green$CARET"
         |""".stripMargin,
    item = "green",
  )

  def testUnionTypeAliasInsideStringLiteralAfterSomeText_PrefixMatchesStart(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "re$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "red$CARET"
         |""".stripMargin,
    item = "red",
  )

  def testUnionTypeAliasInsideStringLiteralAfterSomeText_PrefixMatchesMiddle(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "re$CARET"
         |""".stripMargin,
    resultText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "green$CARET"
         |""".stripMargin,
    item = "green",
  )

  def testUnionTypeAliasInsideStringLiteralAfterSpaces(): Unit = doOptimisticCompletionTest(
    fileText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "  $CARET"
         |""".stripMargin,
    resultText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "blue$CARET"
         |""".stripMargin,
    item = "blue",
  )

  def testNoCompletionInStringLiteralAfterDollar(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "$$$CARET"
         |""".stripMargin,
    item = "blue",
  )
}

final class Scala2LiteralTypeValuesCompletionTest extends Scala3LiteralTypeValuesCompletionTest {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala2

  // no suggestions in Scala 2 expected
  override protected def doOptimisticCompletionTest(fileText: String, resultText: String, item: String): Unit =
    checkNoBasicCompletion(fileText = fileText, item = item)
}
