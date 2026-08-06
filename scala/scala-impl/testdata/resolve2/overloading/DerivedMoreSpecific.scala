object Test extends Application {
  class A {
    def foo(x: Int)(z: String) = print(1)
  }

  class B extends A {
    def foo(x: Int) = print(2)
  }

  val b = new B
  import b._

  /* line: 7 */foo(3)
}