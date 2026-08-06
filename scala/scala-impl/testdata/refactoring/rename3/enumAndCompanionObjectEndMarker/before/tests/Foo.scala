package tests

enum /*caret*/Foo:
  case A, B
  case C(i: Int)
end Foo

object Foo/*caret*/:
  def apply() = Foo.A
end Foo

object Test:
  val foo = Foo/*caret*/()
end Test
