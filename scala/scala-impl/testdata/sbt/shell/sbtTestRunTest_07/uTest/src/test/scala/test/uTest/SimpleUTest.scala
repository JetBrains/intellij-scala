package test.uTest

import utest._

object SimpleUTest extends TestSuite {
  val tests = TestSuite {
    "First uTest" - {
      println("Marker: uTest first test")
    }

    "Second uTest" - {
      println("Marker: uTest second test")
    }

    "First" - {
      println("Marker: uTest prefix test")
    }
  }
}
