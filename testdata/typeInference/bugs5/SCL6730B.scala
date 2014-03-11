object SCL6730B {
  case class A(x: Int)(implicit y: Int)

  class O {
    def foo(x: Int => Any) = 1
    def foo(x: Any) = "text"

    /*start*/foo(A)/*end*/
  }
}
//String