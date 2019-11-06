package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class ConstructorTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testScl7255(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |// private constructor
         |class Recipe private(val ingredients: List[String] = List.empty,
         |                     val directions: List[String] = List.empty) {
         |  println("something")
         |}
         |
         |object Recipe {
         |  def make(ingredients: List[String], directions: List[String]): Recipe =
         |    new Recipe(ingredients, directions)
         |}
         |
         |object Cookbook {
         |  // no warnings
         |  val pbj = new Recipe($CARET
         |    List("peanut butter", "jelly", "bread"),
         |    List("put the peanut butter and jelly on the bread"))
         |}
      """.stripMargin)
  }

}