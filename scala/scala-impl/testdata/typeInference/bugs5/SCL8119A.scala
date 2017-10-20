object SCL8119A {
  trait A[+This] {
    def foo[TT >: DV[Int]](implicit z: Z[TT]): TT = ???
  }
  class V[T] extends A[V[T]]
  class DV[T] extends V[T] with A[DV[T]] {
    def x = 1
  }

  class Z[T]

  object V {
    implicit def repr[T]: Z[V[T]] = ???
    implicit def repr1[T]: Z[DV[T]] = ???
  }

  val dv: DV[Int] = ???

  /*start*/dv.foo.x/*end*/
}
//Int