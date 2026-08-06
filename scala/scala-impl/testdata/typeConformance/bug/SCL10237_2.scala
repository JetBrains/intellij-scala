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

  type T[P] = Map[List[String], P]
  val arg: Cartesian[T] = ???
  /*caret*/
  val x: Unapply.Aux1[Cartesian, T[Int], T, Int] = unapply1(arg)
}
//True