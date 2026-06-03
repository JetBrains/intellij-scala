object Test extends Application {
  class C
  class B extends C
  class A {
    val foo = new {
      def apply(x: C, y: B) = print(1)
      def apply(x: B, y: C) = print(2)
    }
    def foo(x: Boolean) = 1
  }

  val a = new A

 a./* line: 5 */foo(new B, new B)
}