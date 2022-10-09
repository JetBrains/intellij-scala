package org.jetbrains.plugins.scala.testingSupport.scalatest.base.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait WordSpecStaticStringTest extends ScalaTestTestCase {

  private val ClassName = "WordSpecStringTest"
  private val FileName = ClassName + ".scala"

  addSourceFile(FileName,
    s"""$ImportsForWordSpec
       |
       |class $ClassName extends $WordSpecBase {
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
    assertConfigAndSettings(createTestCaretLocation(17, 10, FileName), ClassName, "sum name should test")
  }

  def testWordSpecVal(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(6, 10, FileName), ClassName, "const should const")
  }

  def testWordSpecValSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(14, 10, FileName), ClassName, "sum name should constconst")
    assertConfigAndSettings(createTestCaretLocation(9, 10, FileName), ClassName, "const should const sum")
  }

  def testWordSpecNonConst(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(19, 10, FileName), ClassName, "sum name should constconst", "sum name should test")
  }
}
