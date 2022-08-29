package tests

class Foo(i: Int):
  def bar(x: Int) = i + x
  def this/*caret*/() =
    this(42)
  end this
  def /*caret*/this(str: String) =
    this(str.length)
  end this
end Foo

object Test:
  val foo1 = Foo(1)
  val foo2 = Foo()
  val foo3 = Foo("foo")
end Test
