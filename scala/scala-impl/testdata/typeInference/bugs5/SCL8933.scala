case class R[F[_], T](f: F[T])
class B[T]
val r = R(new B[Int])
/*start*/r/*end*/
//R[B, Int]