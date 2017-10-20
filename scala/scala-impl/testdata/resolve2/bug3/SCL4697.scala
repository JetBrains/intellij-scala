class Bar {
  def foo(baz: Boolean) { }
  def foo(o: Object) { }

  def other {
    foo(/* line: 2 */baz = true) // baz is red, code compiles
  }
}