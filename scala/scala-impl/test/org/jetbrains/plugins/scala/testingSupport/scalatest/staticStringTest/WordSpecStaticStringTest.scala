package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 26.06.2015.
  */
trait WordSpecStaticStringTest extends ScalaTestTestCase {
  val wordSpecClassName = "WordSpecStringTest"
  val wordSpecFileName = wordSpecClassName + ".scala"

  addSourceFile(wordSpecFileName,
    s"""
      |import org.scalatest._
      |
      |class $wordSpecClassName extends WordSpec {
      |  val constName = "const"
      |
      |  constName should {
      |    constName in {
      |    }
      |
      |    constName + " sum" in {
      |    }
      |  }
      |
      |  "sum " + "name" should {
      |    constName + constName in {
      |    }
      |
      |    "test" in {}
      |
      |    const + System.currentTimeMillis() in {
      |    }
      |  }
      |}
      |
    """.stripMargin.trim())

  def testWordSpecSum() = {
    assertConfigAndSettings(createTestFromLocation(17, 10, wordSpecFileName), wordSpecClassName, "sum name should test")
  }

  def testWordSpecVal() = {
    assertConfigAndSettings(createTestFromLocation(6, 10, wordSpecFileName), wordSpecClassName, "const should const")
  }

  def testWordSpecValSum() = {
    assertConfigAndSettings(createTestFromLocation(14, 10, wordSpecFileName), wordSpecClassName, "sum name should constconst")
    assertConfigAndSettings(createTestFromLocation(9, 10, wordSpecFileName), wordSpecClassName, "const should const sum")
  }

  def testWordSpecNonConst() = {
    assertConfigAndSettings(createTestFromLocation(19, 10, wordSpecFileName), wordSpecClassName)
  }
}
