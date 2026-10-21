package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.Message.Error

class LiteralPatternAnnotatorTest extends PatternAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testIncompatiblePrimitiveLiteralPatterns(): Unit = assertMessages(
    """def f(s: String): Unit = s match {
      |  case 1 => ()
      |  case 1L => ()
      |  case 1.0 => ()
      |  case true => ()
      |  case 'a' => ()
      |  case _ => ()
      |}
      |""".stripMargin,
    List(
      Error("1", patternTypeIncompatible("Int", "String")),
      Error("1L", patternTypeIncompatible("Long", "String")),
      Error("1.0", patternTypeIncompatible("Double", "String")),
      Error("true", patternTypeIncompatible("Boolean", "String")),
      Error("'a'", patternTypeIncompatible("Char", "String"))
    )
  )

  def testIncompatibleStringLiteralPattern(): Unit = assertMessages(
    """def f(i: Int): Unit = i match {
      |  case "a" => ()
      |  case _ => ()
      |}
      |""".stripMargin,
    List(Error("\"a\"", patternTypeIncompatible("String", "Int")))
  )

  def testIncompatibleLiteralInExtractor(): Unit = assertMessages(
    """def f(value: Option[String]): Unit = value match {
      |  case Some(1) => ()
      |  case _ => ()
      |}
      |""".stripMargin,
    List(Error("1", patternTypeIncompatible("Int", "String")))
  )

  def testIncompatibleAliasedLiteralStablePattern(): Unit = assertMessages(
    """type One = 1
      |val one: One = 1
      |def f(s: String): Unit = s match {
      |  case `one` => ()
      |  case _ => ()
      |}
      |""".stripMargin,
    List(Error("`one`", patternTypeIncompatible("Int", "String")))
  )

  def testCompatibleLiteralPatterns(): Unit = assertNoMessages(
    """def intPattern(i: Int): Unit = i match {
      |  case 1 => ()
      |  case _ => ()
      |}
      |def stringPattern(s: String): Unit = s match {
      |  case "a" => ()
      |  case _ => ()
      |}
      |def booleanPattern(b: Boolean): Unit = b match {
      |  case true => ()
      |  case false => ()
      |}
      |def extractorPattern(value: Option[Int]): Unit = value match {
      |  case Some(1) => ()
      |  case _ => ()
      |}
      |""".stripMargin
  )

  def testCompatibleAliasedLiteralStablePattern(): Unit = assertNoMessages(
    """type One = 1
      |val one: One = 1
      |def f(i: Int): Unit = i match {
      |  case `one` => ()
      |  case _ => ()
      |}
      |""".stripMargin
  )

  def testNumericLiteralPatterns(): Unit = assertNoMessages(
    """def intPattern(i: Int): Unit = i match {
      |  case 1L => ()
      |  case 2.0 => ()
      |  case _ => ()
      |}
      |def longPattern(l: Long): Unit = l match {
      |  case 1 => ()
      |  case _ => ()
      |}
      |""".stripMargin
  )
}
