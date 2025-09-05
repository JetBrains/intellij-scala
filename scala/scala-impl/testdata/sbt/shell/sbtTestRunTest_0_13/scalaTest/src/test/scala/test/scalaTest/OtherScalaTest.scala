package test.scalaTest

import org.scalatest._

class OtherScalaTest extends FreeSpec {
  "ScalaTest" - {
    "First test" in {
      println("Marker: ScalaTest Other first test")
    }

    "Second" in {
      println("Marker: ScalaTest Other prefix test")
    }
  }
}
