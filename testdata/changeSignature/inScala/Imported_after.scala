object Test {
  def foo(i: Int) {}
}

class A {
  import Test.foo
  foo(0)
}

class B {
  import Test.{foo => baz}
  baz(0)
}