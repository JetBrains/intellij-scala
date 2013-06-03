object ExpectedOption {
  def foo(x: B => String => Unit) = "text"
  def foo(z: String) = 1

  class B {
    var foo: String = "text"
  }

  /*start*/foo(_.foo_=)/*end*/
}
//String