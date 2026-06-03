object MethodSpecificity {
  case class C(s: String)

  implicit def m[A, R](f: A => R) = C("Function1")

  implicit def m[M[X] <: Seq[X], A](l: M[A]) = C("Seq")

  println(/*start*/m(List(1))/*end*/)
}
//MethodSpecificity.C