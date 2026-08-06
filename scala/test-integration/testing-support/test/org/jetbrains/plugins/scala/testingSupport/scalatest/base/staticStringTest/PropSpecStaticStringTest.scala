package org.jetbrains.plugins.scala.testingSupport.scalatest.base.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait PropSpecStaticStringTest extends ScalaTestTestCase {

  private val propSpecClassName = "PropSpecStringTest"
  private val propSpecFileName = propSpecClassName + ".scala"

  addSourceFile(propSpecFileName,
    s"""$ImportsForPropSpec
       |
       |class $propSpecClassName extends $PropSpecBase {
       |  val constName = "const"
       |  property(constName) {
       |  }
       |
       |  property(constName + " test name") {
       |  }
       |
       |  property("string" + " sum") {
       |  }
       |
       |  property("time: " + System.currentTimeMillis()) {
       |  }
       |}
       |""".stripMargin
  )

  def testPropSpecSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(10, 10, propSpecFileName), propSpecClassName, "string sum")
  }

  def testPropSpecVal(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(4, 10, propSpecFileName), propSpecClassName, "const")
  }

  def testPropSpecValSum(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(7, 10, propSpecFileName), propSpecClassName, "const test name")
  }

  def testPropSpecNonConst(): Unit = {
    assertConfigAndSettings(createTestCaretLocation(13, 10, propSpecFileName), propSpecClassName)
  }
}
