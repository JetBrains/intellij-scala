object Test {
  def foo[T](a: Int)(c: Int, b: Boolean): Unit = ()

  foo[Int](1)(2, true)
  foo(1)(2, true)
}
