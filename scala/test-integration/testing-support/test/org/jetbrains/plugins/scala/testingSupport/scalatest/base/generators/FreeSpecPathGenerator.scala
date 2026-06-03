package org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FreeSpecPathGenerator extends ScalaTestTestCase {

  protected val freeSpecPathClassName = "FreeSpecPathTest"
  protected val freeSpecPathFileName = freeSpecPathClassName + ".scala"

  addSourceFile(freeSpecPathFileName,
    s"""$ImportsForPathFreeSpec
       |
       |class $freeSpecPathClassName extends $PathFreeSpecBase {
       |  "A FreeSpecTest" - {
       |    "should be able to run single test" in {
       |      print("$TestOutputPrefix OK $TestOutputSuffix")
       |    }
       |
       |    "should not run tests that are not selected" in {
       |      print("nothing interesting: path.FreeSpec executes contexts anyway")
       |    }
       |  }
       |}
       |""".stripMargin.trim())
}
