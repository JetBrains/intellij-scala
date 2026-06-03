class SCL4823 {
  trait A

  def fooz(x: Array[A]): Int = 1
  def fooz(x: Boolean): Boolean = x

  /*start*/fooz(Array(new Object with A))/*end*/
}
//Int