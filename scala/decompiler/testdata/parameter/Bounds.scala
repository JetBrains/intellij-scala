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

  type AbstractTypeLowerBound[A >: Int]

  type AbstractTypeUpperBound[A <: Int]

  type AbstractTypeLowerAndUpperBounds[A >: Int <: AnyVal]

  type TypeAliasLowerBound[A >: Int] = Int

  type TypeAliasUpperBound[A <: Int] = Int

  type TypeAliasLowerAndUpperBounds[A >: Int <: AnyVal] = Int
}