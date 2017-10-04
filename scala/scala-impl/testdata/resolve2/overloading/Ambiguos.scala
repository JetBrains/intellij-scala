object Test extends Application {
  object A {
    def foo(x: Int) = print(1)
  }
  class A

  object B {
    def foo(x: Int) = print(2)
  }
  class B

  import A._, B._

  /* resolved: false */foo(3)
}