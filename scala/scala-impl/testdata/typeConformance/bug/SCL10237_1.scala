trait Unapply[TC[_[_]], MA] extends Serializable {
  type M[_]
  type A

  def foo() = 42
}

class Cartesian[A[B]] {
  def foo() = {}
}

object Unapply {
  type Aux1[TC[_[_]], MA, F[_], AA] = Unapply[TC, MA] {
    type M[X] = F[X]
    type A = AA
  }

  implicit def unapply1[TC[_[_]], F[_], AA](implicit tc: TC[F]): Aux1[TC,F[AA],F,AA] = ???

  type K[T] = Map[T, Int]
  val c: Cartesian[K] = ???
  /*caret*/
  val b: Unapply[Cartesian, K[List[String]]] = unapply1(c)
}
//True