package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.IntegrationTest

/**
 * @author Roman.Shein
 * @since 10.02.2015.
 */
trait FreeSpecGenerator extends IntegrationTest {
  def addFreeSpec() {
    addFileToProject("FreeSpecTest.scala",
      """
        |import org.scalatest._
        |
        |class FreeSpecTest extends FreeSpec {
        |  "A FreeSpecTest" - {
        |    "should be able to run single tests" in {
        |      print(">>TEST: OK<<")
        |    }
        |
        |    "should not run tests that are not selected" in {
        |      print(">>TEST: FAILED<<")
        |    }
        |  }
        |}
      """.stripMargin.trim()
    )
  }

  def addComplexFreeSpec() = {
    addFileToProject("ComplexFreeSpec.scala",
    """
      |import org.scalatest._
      |
      |class ComplexFreeSpec extends FreeSpec {
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
    """.stripMargin.trim())
  }
}
