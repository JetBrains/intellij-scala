package org.jetbrains.plugins.scala.testingSupport.scalatest.base.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FreeSpecStaticStringTest extends ScalaTestTestCase {

  private val ClassName = "FreeSpecStringTest"
  private val FileName = ClassName + ".scala"

  addSourceFile(FileName,
    s"""$ImportsForFreeSpec
      |
      |class $ClassName extends $FreeSpecBase {
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
      |""".stripMargin
  )

  def testFreeSpecSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(8, 7, FileName), ClassName, "A FreeSpecTest should work with sum")
  }

  def testFreeSpecVal(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(16, 7, FileName), ClassName, "Const name innerNonConst")
    assertConfigAndSettings(createTestCaretLocation(19, 7, FileName), ClassName, "Const name InnerConst")
  }

  def testFreeSpecValSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(11, 7, FileName), ClassName, "A FreeSpecTest should work with sum of consts")
  }

  def testFreeSpecNonConst(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(24, 7, FileName), ClassName)
  }
}
