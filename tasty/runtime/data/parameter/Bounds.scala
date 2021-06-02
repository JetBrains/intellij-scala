package parameter

trait Bounds {
  class ClassLowerBound[A >: Int]

  class ClassUpperBound[A <: Int]

  class ClassLowerAndUpperBounds[A >: Int <: Int]

  trait TraitLowerBound[A >: Int]

  trait TraitUpperBound[A <: Int]

  trait TraitLowerAndUpperBounds[A >: Int <: Int]

  def defLowerBound[A >: Int]: Unit

  def defUpperBound[A <: Int]: Unit

  def defLowerAndUpperBounds[A >: Int <: Int]: Unit

  enum EnumLowerBound[A >: Int] {
    case Case extends EnumLowerBound[Int]
  }

  enum EnumUpperBound[A <: Int] {
    case Case extends EnumUpperBound[Int]
  }

  enum EnumLowerAndUpperBounds[A >: Int <: Int] {
    case Case extends EnumLowerAndUpperBounds[Int]
  }

  enum EnumCaseClassLowerBound {
    case CaseClass[A >: Int]()
  }

  enum EnumCaseClassUpperBound {
    case CaseClass[A <: Int]()
  }

  enum EnumCaseClassLowerAndUpperBounds {
    case CaseClass[A >: Int <: Int]()
  }
}