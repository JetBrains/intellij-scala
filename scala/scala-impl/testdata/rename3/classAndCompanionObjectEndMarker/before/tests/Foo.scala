package tests

class /*caret*/Foo:
  def bar: Int = 42
end Foo

object Foo/*caret*/:
  def apply() = new Foo/*caret*/()
end Foo

class Bar extends Foo/*caret*/:
  val baz = "..."
end Bar

object Test:
  val foo = Foo/*caret*/()
  val bar = new Bar()
end Test
