object Test {
  def <caret>foo[T](a: Int)(b: Boolean, c: Int): Unit = ()

  foo[Int](1)(true, 2)
  foo(1)(true, 2)
}
