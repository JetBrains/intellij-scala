package test.uTest

import utest._

object OtherUTest extends TestSuite {
  val tests = TestSuite {
    "First Other uTest" - {
      println("Marker: uTest Other first test")
    }
  }

  val otherTests = TestSuite {
    "Second" - {
      println("Marker: uTest Other prefix test")
    }

    "Nested" - {
      "Nested2" - {
        println("Marker: nested test")
      }
    }
  }
}
