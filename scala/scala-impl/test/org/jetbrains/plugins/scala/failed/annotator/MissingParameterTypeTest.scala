package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Nikolay.Tropin
  */
class MissingParameterTypeTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testScl4692(): Unit = {
    val text =
     s"""object Test {
        |  def processStrings(strings: String*) = 1.23
        |
        |  val processAny = processStrings(${CARET}_: _*)
        |}
      """.stripMargin
    //missing parameter type for expanded function ((x$1) => processStrings((x$1: _*)))
    checkHasErrorAroundCaret(text)
  }

  def testScl10539(): Unit = {
    val text =
      s"""class Foo(x: Int)(y: Int)
         |val foo = new Foo$CARET(1)
      """.stripMargin
    checkHasErrorAroundCaret(text)
  }

  def testScl5943(): Unit = {
    val text =
      s"""List("A", "B").foreach {it =>
          |    println("it = " + ${CARET}_)
          |  }
      """.stripMargin
    checkHasErrorAroundCaret(text)
  }
}
