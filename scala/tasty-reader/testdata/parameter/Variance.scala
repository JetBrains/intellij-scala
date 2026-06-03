package parameter

trait Variance {
  class ClassCovariant[+A]

  class ClassContravariant[-A]

  trait TraitCovariant[+A]

  trait TraitContravariant[-A]

  enum EnumCovariant[+A] {
    case Case/**//* extends EnumCovariant[Nothing]*/
  }

  enum EnumContravariant[-A] {
    case Case/**//* extends EnumContravariant[Any]*/
  }

  enum EnumCaseClassCovariant {
    case Class[+A]()
  }

  enum EnumCaseClassContravariant {
    case Class[-A]()
  }

  type AbstractTypeCovariant[+A]

  type AbstractTypeContravariant[-A]

  type TypeAliasCovariant[+A] = Int

  type TypeAliasContravariant[-A] = Int
}