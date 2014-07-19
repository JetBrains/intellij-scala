object SCL7278 {

  class Z {

    sealed trait Foo

    case object Bar extends Foo

  }

  class X(val z: Z) {
    def foo(x: Int): Int = 123
    def foo(x: z.Foo): String = "text"
    def m = foo(z.Bar)
  }

  case class Y(z: Z) {
    def foo(x: Int): Int = 123
    def foo(x: z.Foo): String = "text"
    def m = foo(z.Bar)
  }

  class Y2(z: Z) {
    def foo(x: Int): Int = 123
    def foo(x: z.Foo): String = "text"
    def m = foo(z.Bar)
  }

  val z: Z = new Z
  /*start*/(new X(z).m, new Y(z).m, new Y2(z).m)/*end*/

}
//(String, String, String)