class A {
  def foo(x: Int, y: Int = 1) = 1
}

class B extends A {
  override def foo(x: Int, y: Int) = 2
  def foo(x: Int, y: Int, Z: Int) = 4
  /* line: 6 */foo(1)
}