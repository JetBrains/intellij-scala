object SCL9663A {

  class Foo(val cell: String) extends AnyVal {
    def foo(x: Int) = 123
  }

  class Bar(val sheet: String) {

    import Foo._

    /*start*/Foo.hashCode()/*end*/
  }

}
//Int