object SCL5661 {

  val y: String = "text"

  def failfunc(x: Map[String, Int]) = 1
  def failfunc(x: String) = "text"

  /*start*/failfunc(Map(y -> 1))/*end*/
}
//Int