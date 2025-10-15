package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
sealed class Scala3LiteralTypeValuesCompletionTest extends ScalaCompletionTestBase {
  protected def doOptimisticCompletionTest(fileText: String, resultText: String, item: String): Unit =
    doCompletionTest(fileText = fileText, resultText = resultText, item = item)

  @Test
  def testUnionTypeVariable(): Unit = doOptimisticCompletionTest(
    fileText = s"val x: 42 | -1 = $CARET",
    resultText = s"val x: 42 | -1 = 42$CARET",
    item = "42",
  )

  @Test
  def testUnionTypeVariable2(): Unit = doOptimisticCompletionTest(
    fileText = s"val x: 42 | -1 = $CARET",
    resultText = s"val x: 42 | -1 = -1$CARET",
    item = "-1",
  )

  @Test
  def testUnionTypeFunction(): Unit = doOptimisticCompletionTest(
    fileText = s"def x(): 42 | -1 = $CARET",
    resultText = s"def x(): 42 | -1 = 42$CARET",
    item = "42",
  )

  @Test
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

  @Test
  def testSingleLiteralType(): Unit = doOptimisticCompletionTest(
    fileText = s"""val x: "literal_string_type" = $CARET""",
    resultText = s"""val x: "literal_string_type" = "literal_string_type"$CARET""",
    item = "\"literal_string_type\"",
  )

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
  @Test
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

  @Test
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

  @Test
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
  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  def testNoCompletionInStringLiteralAfterDollar(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |type Color = "red" | "green" | "blue"
         |val color: Color = "$$$CARET"
         |""".stripMargin,
    item = "blue",
  )
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
final class Scala2LiteralTypeValuesCompletionTest extends Scala3LiteralTypeValuesCompletionTest {
  // no suggestions in Scala 2 expected
  override protected def doOptimisticCompletionTest(fileText: String, resultText: String, item: String): Unit =
    checkNoBasicCompletion(fileText = fileText, item = item)
}
