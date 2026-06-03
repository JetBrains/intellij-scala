package idea

abstract class Specification {
  class Description
}

trait SpecificationBuilder extends Specification

abstract class Client {
  val builder = new Specification with SpecificationBuilder {
    type Den = String
    val popa = 56
    class Yole
  }
  import builder._

  // Class in named element
  val d = new Description

  // Type alias
  val den: D<ref>en

  // Value
  val p = popa

  // Inner class in refinements
  val y: Yole

}
