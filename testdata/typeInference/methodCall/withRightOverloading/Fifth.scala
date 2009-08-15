package mo

object Fifth {
  def foo(x: Int) = 45
  def foo(x: String) = 45
  def foo(x: Int, yy: Int) = 45
  def foo(x: Int, yy: String, z: Int = 34) = 45

  /*start*/foo(23, yy = 56)/*end*/
}
//Int