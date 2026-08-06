object test {
  trait MA[M[_], A] {
    val ma = 1
  }

  {
    implicit def maArrayInt(xs : scala.Array[scala.Int]) : MA[Array, Int] = null
    implicit def maImplicit[M[_], A](a: M[A]): MA[M, A] = null
    Array(1, 2, 3)./* */ma
  }
}