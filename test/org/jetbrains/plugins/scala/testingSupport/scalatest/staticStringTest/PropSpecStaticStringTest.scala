package org.jetbrains.plugins.scala.testingSupport.scalatest.staticStringTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 26.06.2015.
  */
trait PropSpecStaticStringTest extends ScalaTestTestCase {
  val propSpecClassName = "PropSpecStringTest"
  val propSpecFileName = propSpecClassName + ".scala"

  def addPropSpec() = {
    addFileToProject(propSpecFileName,
      """
        |import org.scalatest._
        |
        |class PropSpecStringTest extends PropSpec {
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
  }

  def testPropSpecSum() = {
    addPropSpec()

    assert(checkConfigAndSettings(createTestFromLocation(10, 10, propSpecFileName), propSpecClassName, "string sum"))
  }

  def testPropSpecVal() = {
    addPropSpec()

    assert(checkConfigAndSettings(createTestFromLocation(4, 10, propSpecFileName), propSpecClassName, "const"))
  }

  def testPropSpecValSum() = {
    addPropSpec()

    assert(checkConfigAndSettings(createTestFromLocation(7, 10, propSpecFileName), propSpecClassName, "const test name"))
  }

  def testPropSpecNonConst() = {
    addPropSpec()

    assert(checkConfigAndSettings(createTestFromLocation(13, 10, propSpecFileName), propSpecClassName))
  }
}
