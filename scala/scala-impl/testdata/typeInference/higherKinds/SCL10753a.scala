import scala.language.higherKinds

class Holder[T[_]] {
  // a trait to hold a parametrized type T
  def t: T[Any] = null.asInstanceOf[T[Any]]
}

class X[T] {

  // a method that extract the parametrized type, when it is so.
  def foo[Q, X[_]](implicit x: X[Q] =:= T): Holder[X] = {
    new Holder[X]
  }

}

val o = new X[Seq[String]]
/*start*/o.foo/*end*/
//Holder[Seq]