package parameter

trait Variance {
  class ClassCovariant[+A]

  class ClassContravariant[-A]

  trait TraitCovariant[+A]

  trait TraitContravariant[-A]

  type AbstractTypeCovariant[+A]

  type AbstractTypeContravariant[-A]

  type TypeAliasCovariant[+A] = Int

  type TypeAliasContravariant[-A] = Int
}