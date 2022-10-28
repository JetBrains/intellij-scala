package parameter

trait HKTVariance {
  trait TraitCovariant[A[+X]]

  trait TraitContravariant[A[-X]]

  type TypeCovariant[A[+X]]

  type TypeContravariant[A[-X]]
}