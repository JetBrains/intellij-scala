package org.jetbrains.plugins.scala.testingSupport.scalatest.base.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FlatSpecStaticStringTest extends ScalaTestTestCase {

  private val ClassName = "FlatSpecStringTest"
  private val FileName = ClassName + ".scala"

  addSourceFile(FileName,
    s"""$ImportsForFlatSpec
       |
       |class $ClassName extends $FlatSpecBase with GivenWhenThen {
       |
       |  val nameFragment = "work with consts"
       |  val sumConst = " of consts"
       |  "Static strings" should "accept" + " sums" in {
       |  }
       |
       |  it should nameFragment in {
       |  }
       |
       |  it should "work with sums" + sumConst in {
       |  }
       |
       |  it should "not accept this: " + System.currentTimeMillis() in {
       |  }
       |}
       |""".stripMargin
  )

  def testFlatSpecSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(6, 7, FileName), ClassName,
      "Static strings should accept sums")
  }

  def testFlatSpecVal(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(9, 7, FileName), ClassName,
      "Static strings should work with consts")
  }

  def testFlatSpecValSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(12, 7, FileName), ClassName,
      "Static strings should work with sums of consts")
  }

  def testFlatSpecNonConst(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(15, 7, FileName), ClassName,
      "Static strings should accept sums",
      "Static strings should work with consts",
      "Static strings should work with sums of consts")
  }
}
