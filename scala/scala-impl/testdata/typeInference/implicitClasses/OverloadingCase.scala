object OverloadingCase {
  implicit class foo(x: Int)

  def foo(x: Boolean) = 23

  /*start*/foo(1)/*end*/
}
//OverloadingCase.foo