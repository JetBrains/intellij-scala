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
  val d = new D<ref>escription

  // Type alias
  val den: Den

  // Value
  val p = popa

  // Inner class in refinements
  val y: Yole

}
