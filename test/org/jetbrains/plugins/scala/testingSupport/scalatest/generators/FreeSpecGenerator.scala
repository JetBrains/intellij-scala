package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 10.02.2015.
  */
trait FreeSpecGenerator extends ScalaTestTestCase {

  val freeSpecClassName = "FreeSpecTest"
  val complexFreeSpecClassName = "ComplexFreeSpec"

  val freeSpecFileName = freeSpecClassName + ".scala"
  val complexFreeSpecFileName = complexFreeSpecClassName + ".scala"

  addSourceFile(freeSpecFileName,
    s"""
      |import org.scalatest._
      |
      |class $freeSpecClassName extends FreeSpec {
      |  "A FreeSpecTest" - {
      |    "should be able to run single tests" in {
      |      print(">>TEST: OK<<")
      |    }
      |
      |    "should not run tests that are not selected" in {
      |      print(">>TEST: FAILED<<")
      |    }
      |
      |    "can be tagged" taggedAs(FreeSpecTag) in {}
      |  }
      |}
      |
      |object FreeSpecTag extends Tag("MyTag")
    """.stripMargin.trim()
  )

  addSourceFile(complexFreeSpecFileName,
    s"""
      |import org.scalatest._
      |
      |class $complexFreeSpecClassName extends FreeSpec {
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
      |}
    """.stripMargin.trim()
  )
}
