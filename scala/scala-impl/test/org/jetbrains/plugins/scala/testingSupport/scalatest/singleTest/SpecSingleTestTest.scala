package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait SpecSingleTestTest extends ScalaTestTestCase {

  //TODO: stop ignoring this test once finders API is functioning
//  addSourceFile("Spec.scala",
//    """
//      |import org.scalatest._
//      |
//      |class SpecTest extends Spec {
//      |
//      |  object `A SpecTest` {
//      |    object `When launched` {
//      |      def `should run single test` {
//      |        print("$TestOutputPrefix OK $TestOutputSuffix")
//      |      }
//      |
//      |      def `should not run other tests` {
//      |        print("$TestOutputPrefix FAILED $TestOutputSuffix")
//      |      }
//      |    }
//      |  }
//      |}
//    """.stripMargin
//  )
  def __ignored__testSpec(): Unit = {
    runTestByLocation(loc("Spec.scala", 8, 12),
      assertConfigAndSettings(_, "SpecTest", "A SpecTest When launched should run single test"),
      root => {
        assertResultTreeHasExactNamedPath(root, TestNodePath("[root]", "SpecTest", "A SpecTest", "When launched", "should run single test"))
        assertResultTreeDoesNotHaveNodes(root, "should not run other tests")
      }
    )
  }
}
