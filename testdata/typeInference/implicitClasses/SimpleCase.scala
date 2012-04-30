object SimpleCase {
  implicit class A(x: Int) {
    def foo: Int = 1
  }
  /*start*/1.foo/*end*/
}
//Int