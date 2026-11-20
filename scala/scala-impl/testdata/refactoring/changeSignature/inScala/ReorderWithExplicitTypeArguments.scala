object Test {
  def <caret>foo[T](a: Int, b: Boolean): Unit = ()

  foo[Int](1, true)
  Test.foo[Int](1, true)
}
