package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Anton Yalyshev
  */
@Category(Array(classOf[PerfCycleTests]))
class ConstructorTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testScl7255(): Unit = {
    val text =
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
         |  val pbj = ${CARET}new Recipe(
         |    List("peanut butter", "jelly", "bread"),
         |    List("put the peanut butter and jelly on the bread"))
         |}
      """.stripMargin
    checkHasErrorAroundCaret(text)
  }

}
