object Test {
  def <caret>bar() = {}
}

class A {

  import Test.bar

  bar()
}

class B {

  import Test.{bar => baz}

  baz()
}