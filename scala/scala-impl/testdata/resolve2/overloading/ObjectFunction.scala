object Test extends Application {
  class A
  class B extends A
  def foo(x: A, y: B) = print(1)
  object foo {
    def apply(x: B, y: B) = print(3)
    //def apply(x: A, y: A) = print(5)
  }

  /* line: 6, name: apply */foo(new B, new B)
}