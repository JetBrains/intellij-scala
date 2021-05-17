package org.jetbrains.plugins.scala.codeInsight.intention.matcher

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class ConvertToTypedPatternIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = "Convert to typed pattern"

  def test_example(): Unit = doTest(
    text =
      s"""case class Person(name: String, age: Int)
         |
         |p match {
         |  case Perso${CARET}n(name, age) =>
         |}
         |""".stripMargin,
    resultText =
      s"""case class Person(name: String, age: Int)
         |
         |p match {
         |  case person: Person =>
         |}
         |""".stripMargin,
  )

  def test_generic(): Unit = doTest(
    text =
      s"""case class Person[T](value: T)
         |
         |Person(12) match {
         |  case Perso${CARET}n(value) =>
         |}
         |""".stripMargin,
    resultText =
      s"""case class Person[T](value: T)
         |
         |Person(12) match {
         |  case person: Person[Int] =>
         |}
         |""".stripMargin,
  )
}