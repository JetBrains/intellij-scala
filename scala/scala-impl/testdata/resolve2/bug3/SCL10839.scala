class SCL10839 {
  type T

  type Result[T] = P[Xor[String,T]]

  def foo[F[_], A, B](arg: F[Xor[A, B]]): Int = ???
  val b:Result[T] = ???
  /*resolved: true*/foo(b)
}

sealed abstract class Xor[+A, +B]

class P[+T]