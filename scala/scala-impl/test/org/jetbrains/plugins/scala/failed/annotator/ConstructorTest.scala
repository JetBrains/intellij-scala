package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Anton Yalyshev
  */
@Category(Array(classOf[PerfCycleTests]))
class ConstructorTest extends BadCodeGreenTestBase {

  override protected def shouldPass: Boolean = false

  import CodeInsightTestFixture.CARET_MARKER

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
         |  val pbj = ${CARET_MARKER}new Recipe(
         |    List("peanut butter", "jelly", "bread"),
         |    List("put the peanut butter and jelly on the bread"))
         |}
      """.stripMargin
    doTest(text)
  }

}
