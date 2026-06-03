class A {
  def foo: Int = 45
}
object Test {
  new A {
    /* */foo
  }
}