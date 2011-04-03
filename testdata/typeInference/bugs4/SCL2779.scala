object test {
  trait A { def apply(x: Int) = x}
  trait B { def apply(x: Int) = x}
  trait C { }
  implicit def A2B(a: A): B = null
  implicit def C2B(a: C): B = null
  def foo[A]: A = null

  /*start*/(foo[A](1), foo[B](1), foo[C](1), null.asInstanceOf[A](1))/*end*/

}

//(Int, Int, Int, Int)