class GenericFunInput {
  def foo {
    def goo[T](x: T) = x
    /*start*/
    val x = goo(3)
    val y = goo("")
    val z = goo[Long](3L)
    /*end*/
    val f = x
    val h = y
    val u = z
  }
}
/*
class GenericFunInput {
  def foo {
    def goo[T](x: T) = x
    /*start*/
    def testMethodName: (Int, String, Long) = {
      val x = goo(3)
      val y = goo("")
      val z = goo[Long](3L)
      (x, y, z)
    }
    val r = testMethodName
    val x: Int = r._1
    val y: String = r._2
    val z: Long = r._3
    /*end*/
    val f = x
    val h = y
    val u = z
  }
}
*/