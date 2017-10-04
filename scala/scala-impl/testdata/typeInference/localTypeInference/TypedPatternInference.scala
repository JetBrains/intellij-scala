object K {
  class B
  class A[T <: B]

  val x: Any = new A[B]

  def foo(x: A[_ <: B]) = 1
  def foo(x: Int) = false

  x match {
    case a: A[_] =>
      /*start*/foo(a)/*end*/
      1
    case _ =>
  }
}
//Int