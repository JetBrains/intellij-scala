object SCL4662 {
  def f(x: String) = "text"

  def f(a: Int, b: Int) = 0
  /*start*/f(a = 4, 5)/*end*/
}
//Int