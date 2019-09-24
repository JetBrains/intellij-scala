package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait PropSpecStaticStringTest extends ScalaTestTestCase {
  val propSpecClassName = "PropSpecStringTest"
  val propSpecFileName = propSpecClassName + ".scala"

  addSourceFile(propSpecFileName,
    s"""
      |import org.scalatest._
      |
      |class $propSpecClassName extends PropSpec {
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
    """.stripMargin.trim()
  )

  def testPropSpecSum(): Unit = {
    assertConfigAndSettings(createTestFromLocation(10, 10, propSpecFileName), propSpecClassName, "string sum")
  }

  def testPropSpecVal(): Unit = {
    assertConfigAndSettings(createTestFromLocation(4, 10, propSpecFileName), propSpecClassName, "const")
  }

  def testPropSpecValSum(): Unit = {
    assertConfigAndSettings(createTestFromLocation(7, 10, propSpecFileName), propSpecClassName, "const test name")
  }

  def testPropSpecNonConst(): Unit = {
    assertConfigAndSettings(createTestFromLocation(13, 10, propSpecFileName), propSpecClassName)
  }
}
