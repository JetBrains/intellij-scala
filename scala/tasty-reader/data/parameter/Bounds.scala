package parameter

trait Bounds {
  class ClassLowerBound[A >: Int]

  class ClassUpperBound[A <: Int]

  class ClassLowerAndUpperBounds[A >: Int <: AnyVal]

  trait TraitLowerBound[A >: Int]

  trait TraitUpperBound[A <: Int]

  trait TraitLowerAndUpperBounds[A >: Int <: AnyVal]

  def defLowerBound[A >: Int]: Unit

  def defUpperBound[A <: Int]: Unit

  def defLowerAndUpperBounds[A >: Int <: AnyVal]: Unit

  enum EnumLowerBound[A >: Int] {
    case Case extends EnumLowerBound[Int]
  }

  enum EnumUpperBound[A <: Int] {
    case Case extends EnumUpperBound[Int]
  }

  enum EnumLowerAndUpperBounds[A >: Int <: AnyVal] {
    case Case extends EnumLowerAndUpperBounds[Int]
  }

  enum EnumCaseClassLowerBound {
    case CaseClass[A >: Int]()
  }

  enum EnumCaseClassUpperBound {
    case CaseClass[A <: Int]()
  }

  enum EnumCaseClassLowerAndUpperBounds {
    case CaseClass[A >: Int <: AnyVal]()
  }

  type AbstractTypeLowerBound[A >: Int]

  type AbstractTypeUpperBound[A <: Int]

  type AbstractTypeLowerAndUpperBounds[A >: Int <: AnyVal]

  type TypeAliasLowerBound[A >: Int] = Int

  type TypeAliasUpperBound[A <: Int] = Int

  type TypeAliasLowerAndUpperBounds[A >: Int <: AnyVal] = Int

  extension [A >: Int](i: Int)
    def extensionLowerBound: Unit = ???

  extension [A <: Int](i: Int)
    def extensionUpperBound: Unit = ???

  extension [A >: Int <: AnyVal](i: Int)
    def extensionLowerAndUpperBounds: Unit = ???

  extension (i: Int)
    def extensionMethodLowerBound[A >: Int]: Unit = ???

  extension (i: Int)
    def extensionMethodUpperBound[A <: Int]: Unit = ???

  extension (i: Int)
    def extensionMethodLowerAndUpperBounds[A >: Int <: AnyVal]: Unit = ???

  trait T

  given givenAliasLowerBound[A >: Int]: T = ???

  given givenAliasUpperBound[A <: Int]: T = ???

  given givenAliasLowerAndUpperBounds[A >: Int <: AnyVal]: T = ???

  given givenInstanceLowerBound[A >: Int]: T with {}

  given givenInstanceUpperBound[A <: Int]: T with {}

  given givenInstanceLowerAndUpperBounds[A >: Int <: AnyVal]: T with {}
}