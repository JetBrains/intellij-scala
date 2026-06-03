object test {
  implicit def u[M[_], A](a : M[A]) = new { def foo = 0}
  implicit def intArrayOps(xs: Array[Int]) = new { def foo = 0}

  /*start*/Array(1).foo/*end*/ // ambiguous implicit view
}
//Int