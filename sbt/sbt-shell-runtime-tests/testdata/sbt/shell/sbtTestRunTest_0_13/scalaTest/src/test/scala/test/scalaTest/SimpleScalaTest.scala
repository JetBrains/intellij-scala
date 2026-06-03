package test.scalaTest

import org.scalatest._

class SimpleScalaTest extends FreeSpec {
  "ScalaTest" - {
    "First test" in {
      println("Marker: ScalaTest first test")
    }

    "Second test" in {
      println("Marker: ScalaTest second test")
    }

    "First" in {
      println("Marker: ScalaTest prefix test")
    }
  }
}
