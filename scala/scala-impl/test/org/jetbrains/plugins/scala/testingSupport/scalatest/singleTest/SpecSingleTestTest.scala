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
//      |        print(">>TEST: OK<<")
//      |      }
//      |
//      |      def `should not run other tests` {
//      |        print(">>TEST: FAILED<<")
//      |      }
//      |    }
//      |  }
//      |}
//    """.stripMargin
//  )
  def __ignored__testSpec(): Unit = {
    runTestByLocation(8, 12, "Spec.scala",
      checkConfigAndSettings(_, "SpecTest", "A SpecTest When launched should run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "SpecTest", "A SpecTest", "When launched", "should run single test") &&
          checkResultTreeDoesNotHaveNodes(root, "should not run other tests"),
      debug = true
    )
  }
}
