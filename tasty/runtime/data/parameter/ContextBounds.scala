package parameter

trait ContextBounds {
  class ClassLowerBound[A/**/ : Ordering/**/]/**//*(implicit evidence$1: Ordering[A])*/

  trait TraitLowerBound[A/**/ : Ordering/**/]/**//*(implicit evidence$2: Ordering[A])*/

  def defLowerBound[A/**/ : Ordering/**/]/**//*(implicit evidence$3: Ordering[A])*/: Unit

  enum EnumLowerBound[A/**/ : Ordering/**/]/**//*(implicit evidence$4: Ordering[A])*/ {
    case Case extends EnumLowerBound[Int]
  }

  enum EnumCaseClassLowerBound {
    case CaseClass[A/**/ : Ordering/**/]()/**//*(implicit evidence$9: Ordering[A])*/
  }

  extension [A/**/ : Ordering/**/](i: Int)
    def extensionLowerBound/**//*(implicit evidence$5: Ordering[A])*/: Unit = ???

  extension (i: Int)
    def extensionMethodLowerBound[A/**/ : Ordering/**/]/**//*(implicit evidence$6: Ordering[A])*/: Unit = ???

  trait T

  given givenAliasLowerBound[A/**/ : Ordering/**/]/**//*(implicit evidence$7: Ordering[A])*/: T = ???

  given givenInstanceLowerBound[A/**/ : Ordering/**/]/**//*(implicit evidence$8: Ordering[A])*/: T with {}
}