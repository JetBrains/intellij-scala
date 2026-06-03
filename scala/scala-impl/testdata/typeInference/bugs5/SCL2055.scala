class SCL2055 {
  def m2(p: String) = p
  def m2(p: => Unit) = 1
  /*start*/m2()/*end*/
}
//Int