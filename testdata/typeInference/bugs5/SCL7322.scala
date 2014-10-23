object A {
  implicit def foo(i: Int)(j: Int): Int = i + j

  def m(i: Int => Int) = 1
  def m(x: String) = x
  /*start*/m(1)/*end*/
}
//Int