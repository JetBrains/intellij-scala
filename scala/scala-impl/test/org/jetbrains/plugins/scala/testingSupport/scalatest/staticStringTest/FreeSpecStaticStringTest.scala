package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait FreeSpecStaticStringTest extends ScalaTestTestCase {

  val freeSpecClassName = "FreeSpecStringTest"
  val freeSpecFileName = freeSpecClassName + ".scala"

  addSourceFile(freeSpecFileName,
    s"""
      |import org.scalatest._
      |
      |class $freeSpecClassName extends FreeSpec {
      |  val constName = " consts"
      |  val otherConstName = "Const name"
      |  val innerConst = "InnerConst"
      |
      |  "A" + " FreeSpecTest" - {
      |    "should work with sum" in {
      |    }
      |
      |    "should work with sum of" + constName in {
      |    }
      |  }
      |
      |  otherConstName - {
      |    "innerNonConst" in {
      |    }
      |
      |    innerConst in {
      |    }
      |  }
      |
      |  "base " + foo() - {
      |    "unreachable" in {
      |    }
      |  }
      |
      |  def foo(): String = "foo"
      |}
    """.stripMargin.trim()
  )

  def testFreeSpecSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(8, 7, freeSpecFileName), freeSpecClassName, "A FreeSpecTest should work with sum")
  }

  def testFreeSpecVal(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(16, 7, freeSpecFileName), freeSpecClassName, "Const name innerNonConst")
    assertConfigAndSettings(createTestCaretLocation(19, 7, freeSpecFileName), freeSpecClassName, "Const name InnerConst")
  }

  def testFreeSpecValSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(11, 7, freeSpecFileName), freeSpecClassName, "A FreeSpecTest should work with sum of consts")
  }

  def testFreeSpecNonConst(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(24, 7, freeSpecFileName), freeSpecClassName)
  }
}
