package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 10.02.2015.
  */
trait PropSpecGenerator extends ScalaTestTestCase {
  val propSpecClassName = "PropSpecTest"
  val propSpecFileName = propSpecClassName + ".scala"

  addSourceFile(propSpecFileName,
    s"""
      |import org.scalatest._
      |
      |class $propSpecClassName extends PropSpec {
      |
      |  property("Single tests should run") {
      |    print(">>TEST: OK<<")
      |  }
      |
      |  property("other test should not run") {
      |    print(">>TEST: FAILED<<")
      |  }
      |
      |  property("tagged", PropSpecTag) {}
      |}
      |
      |object PropSpecTag extends Tag("MyTag")
    """.stripMargin.trim()
  )
}
