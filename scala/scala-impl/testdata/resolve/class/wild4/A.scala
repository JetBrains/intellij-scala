object Foo {
  val IsNull = null
}

object Bar {
  import Foo._
  def foo(a: Any) = null
  def bar = foo(Is<ref>Null)
}