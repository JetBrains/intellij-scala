package org.jetbrains.plugins.scala.codeInsight.intention.matcher

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

sealed abstract class ExpandPatternIntentionTestBase extends ScalaIntentionTestBase {

  override def familyName: String = "Expand to Constructor pattern"

  def test_example(): Unit = doTest(
    text =
      s"""
         |case class Person(name: String, age: Int)
         |
         |p match {
         |  case Seq(perso${CARET}n: Person) => "a person"
         |}
         |""".stripMargin,
    resultText =
      s"""
         |case class Person(name: String, age: Int)
         |
         |p match {
         |  case Seq(perso${CARET}n@Person(name, age)) => "a person"
         |}
         |""".stripMargin
  )

  def test_wildcard(): Unit = doTest(
    text =
      s"""
         |case class Person(name: String, age: Int)
         |
         |p match {
         |  case Seq(_: Per${CARET}son) => "a person"
         |}
         |""".stripMargin,
    resultText =
      s"""
         |case class Person(name: String, age: Int)
         |
         |p match {
         |  case Seq(Person(name, age)) => "a person"
         |}
         |""".stripMargin
  )
}

final class ExpandPatternIntentionTest extends ExpandPatternIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_13
}

final class ExpandPatternIntentionTest_Scala3 extends ExpandPatternIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0
}
