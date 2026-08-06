class C[+T]
class A[+T] extends C[T]
class B extends C[Nothing]

case class D[T](x: T)

val zz = if (true) D(new A[Int]) else D(new B)
/*start*/zz/*end*/
//D[_ >: A[Int] with B <: C[Int]]