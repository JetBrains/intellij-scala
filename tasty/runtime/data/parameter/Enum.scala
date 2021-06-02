package parameter

trait Enum {
  enum TypeParameter[A] {
    case Case extends TypeParameter[Int]
  }

  enum TypeParameters[A, B] {
    case Case extends TypeParameters[Int, Int]
  }

  enum ValueParameter(x: Int) {
    case Case extends ValueParameter(???)
  }

  enum TypeAndValueParameters[A](x: Int) {
    case Case extends TypeAndValueParameters[Int](???)
  }
}