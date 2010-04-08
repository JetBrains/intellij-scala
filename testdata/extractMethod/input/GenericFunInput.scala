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
class GenericFunInput {
  def foo {
    def goo[T](x: T) = x
    /*start*/
    def testMethodName: (Long, Int, String) = {
      val x = goo(3)
      val y = goo("")
      val z = goo[Long](3L)
      (z, x, y)
    }
    val r = testMethodName
    val z: Long = r._1
    val x: Int = r._2
    val y: String = r._3
    /*end*/
    x; y; z
  }
}
*/