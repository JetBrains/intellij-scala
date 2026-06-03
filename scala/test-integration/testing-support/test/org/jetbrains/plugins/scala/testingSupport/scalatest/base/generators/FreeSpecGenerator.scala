package org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait FreeSpecGenerator extends ScalaTestTestCase {

  protected val freeSpecClassName = "FreeSpecTest"
  protected val freeSpecFileName = freeSpecClassName + ".scala"

  protected val complexFreeSpecClassName = "ComplexFreeSpec"
  protected val complexFreeSpecFileName = complexFreeSpecClassName + ".scala"

  addSourceFile(freeSpecFileName,
    s"""$ImportsForFreeSpec
       |
       |class $freeSpecClassName extends $FreeSpecBase {
       |  "A FreeSpecTest" - {
       |    "should be able to run single tests" in {
       |      print("$TestOutputPrefix OK $TestOutputSuffix")
       |    }
       |
       |    "should not run tests that are not selected" in {
       |      print("$TestOutputPrefix FAILED $TestOutputSuffix")
       |    }
       |
       |    "can be tagged" taggedAs(FreeSpecTag) in {}
       |  }
       |}
       |
       |object FreeSpecTag extends Tag("MyTag")
       |""".stripMargin
  )

  addSourceFile(complexFreeSpecFileName,
    s"""$ImportsForFreeSpec
       |
       |class $complexFreeSpecClassName extends $FreeSpecBase {
       |  "A ComplexFreeSpec" - {
       |    "Outer scope 1" - {
       |      "Inner scope 1" in {
       |        val i = 10
       |      }
       |    }
       |
       |    "Outer scope 2" - {
       |      "Inner scope 2" - {
       |        "Innermost scope" ignore {
       |          print("This should not be printed ever")
       |        }
       |
       |        "Another innermost scope" in {
       |        }
       |      }
       |
       |      "Inner test" in {}
       |    }
       |
       |    "Outer scope 3" - {
       |      "Ignored scope 2" - {
       |        "Ignored test" ignore {
       |        }
       |      }
       |    }
       |  }
       |
       |  "Empty scope" - {}
       |
       |  "Not nested scope" in {
       |  }
       |}
       |""".stripMargin
  )
}
