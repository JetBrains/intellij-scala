package org.jetbrains.plugins.scala.codeInsight.intention.matcher

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class ExpandPatternIntentionTest extends ScalaIntentionTestBase {

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