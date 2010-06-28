object Test extends Application {
  class A {
    def foo(x: B) = print(1)
  }

  class B extends A {
    def foo(x: A) = print(2)
  }
  val b = new B

  b./* resolved: false */foo(new B)
}