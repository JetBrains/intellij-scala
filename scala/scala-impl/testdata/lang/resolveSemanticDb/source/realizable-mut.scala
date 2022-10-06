object Foo {
  val x = new Object

  class A(var y: x.type)

  val a = new A(x)

  val y: a.y.type = x
}
