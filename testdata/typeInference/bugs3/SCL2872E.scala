object Underscore {
  type FIntA[A] = (Int => A)
  def foo[A](f: FIntA[A]): A = f(1)
  /*start*/foo(_.toLong)/*end*/
}

// Long