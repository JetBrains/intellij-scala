package parameter

trait Variance() {
  class ClassCovariant[+A]()

  class ClassContravariant[-A]()

  trait TraitCovariant[+A]()

  trait TraitContravariant[-A]()
}