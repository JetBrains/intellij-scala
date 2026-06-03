package tests

object /*caret*/Foo:
  def bar: Int = 42
end Foo

object Test:
  val foo = Foo/*caret*/
  val bar = foo.bar
end Test
