package mo

object First {
  def foo(x: Int) = 45
  def foo(x: String) = 45
  def foo(x: Int, y: Int) = 45
  def foo(x: Int, y: String, z: Int = 34) = 45

  /*start*/foo(56)/*end*/
}
//Int