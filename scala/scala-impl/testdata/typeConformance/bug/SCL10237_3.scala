trait Unapply[TC[_[_]], MA] extends Serializable {
  type M[_]
  type A

  def foo() = 42
}

class Cartesian[A[B]] {
  def foo() = {}
}

trait A1
trait A2 extends A1
trait A3 extends A2

object Unapply {
  type Aux1[TC[_[_]], MA, F[_], AA] = Unapply[TC, MA] {
    type M[X] = F[X]
    type A = AA
  }

  implicit def unapply1[TC[_[_]], F[_], AA >: A3 <: A1](implicit tc: TC[F]): Aux1[TC,F[AA],F,AA] = ???

  type L[X] = List[X]
  type T[P] = Map[List[String], L[P]]
  val arg: Cartesian[T] = ???
  /*caret*/
  val x: Unapply.Aux1[Cartesian, T[A2], T, A2] = unapply1(arg)
}
//True