package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

class AdjustTypesIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = "Adjust types"

  def test_example(): Unit = doTest(
    text =
      s"""
         |val test: sc${CARET}ala.collection.mutable.Seq[String] = ???
         |""".stripMargin,
    resultText =
      s"""
         |val test: scala.collection.mutable.Seq[String] = ???
         |""".stripMargin
  )

  def test_example_2(): Unit = doTest(
    text =
      s"""
         |val test: scala.collection.mutable.S${CARET}eq[String] = ???
         |""".stripMargin,
    resultText =
      s"""
         |val test: scala.collection.mutable.Seq[String] = ???
         |""".stripMargin
  )

  def test_example_3(): Unit = doTest(
    text =
      s"""
         |val test: scala.collect${CARET}ion.mutable.Seq[String] = ???
         |""".stripMargin,
    resultText =
      s"""
         |val test: scala.collection.mutable.Seq[String] = ???
         |""".stripMargin
  )
}
