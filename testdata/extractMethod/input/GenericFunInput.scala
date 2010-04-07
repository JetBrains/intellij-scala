class GenericFunInput {
  def foo {
    def goo[T](x: T) = x
    /*start*/
    val x = goo(3)
    val y = goo("")
    val z = goo[Long](3L)
    /*end*/
    x; y; z
  }
}
/*
*/