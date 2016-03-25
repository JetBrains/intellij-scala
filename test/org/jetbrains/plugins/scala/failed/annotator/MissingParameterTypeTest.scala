package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class MissingParameterTypeTest extends BadCodeGreenTestBase {
  def testScl4692(): Unit = {
    val text =
     s"""object Test {
        |  def processStrings(strings: String*) = 1.23
        |
        |  val processAny = processStrings(${CARET_MARKER}_: _*)
        |}
      """.stripMargin
    //missing parameter type for expanded function ((x$1) => processStrings((x$1: _*)))
    doTest(text)
  }
}
