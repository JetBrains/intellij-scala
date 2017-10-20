object Test extends Application {
  class A {
    def foo(x: Int) = print(1)
    class B extends A {
    }
    object B {
      def foo(x: Int) = print(2)

      def goo = /* line: 7 */foo(2)
    }
  }


  val a = new A
  a.B.goo
}