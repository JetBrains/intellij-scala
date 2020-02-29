package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait WordSpecStaticStringTest extends ScalaTestTestCase {

  val wordSpecClassName = "WordSpecStringTest"
  val wordSpecFileName = wordSpecClassName + ".scala"

  addSourceFile(wordSpecFileName,
    s"""import org.scalatest._
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
       |    constName + System.currentTimeMillis() in {
       |    }
       |  }
       |}
       |""".stripMargin)

  def testWordSpecSum(): Unit = {
    assertConfigAndSettings(createTestFromLocation(17, 10, wordSpecFileName), wordSpecClassName, "sum name should test")
  }

  def testWordSpecVal(): Unit = {
    assertConfigAndSettings(createTestFromLocation(6, 10, wordSpecFileName), wordSpecClassName, "const should const")
  }

  def testWordSpecValSum(): Unit = {
    assertConfigAndSettings(createTestFromLocation(14, 10, wordSpecFileName), wordSpecClassName, "sum name should constconst")
    assertConfigAndSettings(createTestFromLocation(9, 10, wordSpecFileName), wordSpecClassName, "const should const sum")
  }

  def testWordSpecNonConst(): Unit = {
    assertConfigAndSettings(createTestFromLocation(19, 10, wordSpecFileName), wordSpecClassName, "sum name should constconst", "sum name should test")
  }
}
