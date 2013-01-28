object M {
  class M[T <: M[T]]
  class T extends M[T]

  class Z[G, T <: M[T]]
  class S[T <: M[T]] extends Z[String, T]
  val f: S[T] = null
  object A {
    implicit def a[T <: M[T]](s: S[T]): (String, T) = null
    implicit def a[F, T <: M[T]](z: Z[F, T]): (F, T) = null
  }
  import A._

  val s: (String, T) = /*start*/f/*end*/
}
//(String, M.T)