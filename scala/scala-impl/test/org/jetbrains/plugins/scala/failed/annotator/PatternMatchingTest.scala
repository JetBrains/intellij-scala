package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Anton.Yalyshev
  * @since 02.12.2017.
  */
@Category(Array(classOf[PerfCycleTests]))
class PatternMatchingTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL12977() = {
    val text =
      """
        |class Unapply[T](fn: String => T) {
        |  def unapply(s: String): T =
        |    fn(s)
        |}
        |
        |val FirstCapitalLetter: Unapply[Option[Char]] =
        |  new Unapply(s => s.headOption.filter(_.isUpper))
        |
        |"Test" match {
        |  case FirstCapitalLetter(letter) => println(s"Starts with: $letter")
        |  case x => println("Does not start with a capital letter")
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}