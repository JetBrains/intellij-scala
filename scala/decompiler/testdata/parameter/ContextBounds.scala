package parameter

trait ContextBounds {
  class Class[A: Ordering]

  def method[A: Ordering]: Unit

  def multipleParameters[A: Ordering, B: PartialOrdering]: Unit = ???

  def multipleBounds[A: Ordering: PartialOrdering]: Unit = ???

  def typeAndContext[A <: Int: Ordering]: Unit = ???

  def typeParameter1[A, B: Ordering]: Unit = ???

  def typeParameter2[A: Ordering, B]: Unit = ???

  def valueParameter[A: Ordering](x: Int): Unit = ???

  def implicitParameter[A: Ordering](implicit x: Int): Unit = ???

  def notImplicit[A](evidence$1: Ordering[A]): Unit = ???

  class IsSubtypeOf[A, B]

  def multiParameter[A, B](implicit evidence$1: IsSubtypeOf[A, B]): Unit = ???

  def multiParameterInfix[A, B](implicit evidence$1: <:<[A, B]): Unit = ???

  class HKT[A[_]]

  def wildcard[A[_]: HKT]: Unit = ???

  def parameter[A[X]: HKT]: Unit = ???
}