object Test {
  def foo[T](b: Boolean, a: Int): Unit = ()

  foo[Int](true, 1)
  Test.foo[Int](true, 1)
}
