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
}