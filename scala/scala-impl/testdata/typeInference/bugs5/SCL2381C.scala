object MethodSpecificity {
  case class C(s: String)

  implicit def m1[A, R](f: A => R) = C("Function1")

  implicit def m2[M[X] <: Seq[X], A](l: M[A]) = C("Seq")

  println(/*start*/List(1).s/*end*/) // Prints Seq, resolves to m1
}
//String