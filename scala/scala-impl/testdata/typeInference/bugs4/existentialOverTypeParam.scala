class A[V] {
  val x: Class[V]
  def g(x: Class[_ <: V]): Int = 1
  def g(x: Int): Boolean = false
  def gg(x: Class[q] forSome {type q <: V}): Int = 1
  def gg(x: Int): Boolean = false

  /*start*/g(x) + gg(x)/*end*/
}
//Int