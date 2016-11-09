package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 10.02.2015.
  */
trait FreeSpecPathGenerator extends ScalaTestTestCase {
  val freeSpecPathClassName = "FreeSpecPathTest"
  val freeSpecPathFileName = freeSpecPathClassName + ".scala"

  addSourceFile(freeSpecPathFileName,
    s"""
      |import org.scalatest._
      |
      |class $freeSpecPathClassName extends path.FreeSpec {
      |  "A FreeSpecTest" - {
      |    "should be able to run single test" in {
      |      print(">>TEST: OK<<")
      |    }
      |
      |    "should not run tests that are not selected" in {
      |      print("nothing interesting: path.FreeSpec executes contexts anyway")
      |    }
      |  }
      |}
    """.stripMargin.trim())
}
