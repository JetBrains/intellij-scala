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
}