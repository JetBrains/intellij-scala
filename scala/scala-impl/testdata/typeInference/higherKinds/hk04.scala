trait Partial[M[_, _], A] {
  type Apply[B] = M[A, B]
}
case class MA[M[_], A](ma: M[A])
def eitherMA[A, B](e: Either[A, B]): MA[Partial[Either, A]#Apply, B] = new MA[Partial[Either, A]#Apply, B](e)
val l: Either[Int, Boolean] = Left(1)
/*start*/eitherMA(l).ma/*end*/
//Partial[Either, Int]#Apply[Boolean]