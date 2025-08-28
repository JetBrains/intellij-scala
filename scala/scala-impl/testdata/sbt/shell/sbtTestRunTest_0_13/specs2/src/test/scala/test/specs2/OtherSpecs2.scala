package test.specs2

import org.specs2.mutable.Specification

class OtherSpecs2 extends Specification {
  "Specs2 test" should {
    "First test" in {
      println("Marker: Specs2 Other first test")
      success
    }

    "Second prefix" >> {
      println("Marker: Specs2 Other prefix test")
      success
    }
  }
}
