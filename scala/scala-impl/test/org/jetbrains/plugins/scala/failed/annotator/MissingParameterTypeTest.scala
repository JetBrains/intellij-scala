package org.jetbrains.plugins.scala.failed.annotator

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class MissingParameterTypeTest extends BadCodeGreenTestBase {

  override protected def shouldPass: Boolean = false

  import CodeInsightTestFixture.CARET_MARKER

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

  def testScl10539(): Unit = {
    val text =
      s"""class Foo(x: Int)(y: Int)
         |val foo = new Foo$CARET_MARKER(1)
      """.stripMargin
    doTest(text)
  }

  def testScl5943(): Unit = {
    val text =
      s"""List("A", "B").foreach {it =>
          |    println("it = " + ${CARET_MARKER}_)
          |  }
      """.stripMargin
    doTest(text)
  }
}
