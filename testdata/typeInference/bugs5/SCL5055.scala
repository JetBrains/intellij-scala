case class Foo()

object A {
  def foo(s: Serializable) = 1
  def foo(s: String) = false

  /*start*/foo(Foo())/*end*/
}
//Int