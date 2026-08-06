object Test extends App {
  class A {
    def foo(x: Int) = print(1)
  }

  class B extends A {
  }
  val b = new B
  object B {
    def foo(x: Int) = print(2)
    import b./*line: 3*/foo

    def goo = /*line: 10*/foo(2)
  }


  B.goo
}
