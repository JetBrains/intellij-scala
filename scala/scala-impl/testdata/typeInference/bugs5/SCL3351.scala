object A {
  def foo[T](x : T) : T = x
  def goo(a: { def foo[T <: Comparable[T]](y : T) : T }) = 1
  def goo(s: String) = "text"
  /*start*/goo(this)/*end*/
}
//Int