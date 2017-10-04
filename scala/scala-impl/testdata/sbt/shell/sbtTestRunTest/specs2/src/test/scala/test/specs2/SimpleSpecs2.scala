package test.specs2

import org.specs2.mutable.Specification

class SimpleSpecs2 extends Specification {
  "Specs2 test" should {
    "First test" in {
      println("Marker: Specs2 first test")
      success
    }

    "Second test" ! {
      println("Marker: Specs2 second test")
      success
    }

    "First" >> {
      println("Marker: Specs2 prefix test")
      success
    }
  }
}
