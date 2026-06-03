object test {
  trait A { def apply(x: Int) = x}
  trait B { def apply(x: Int) = x}
  trait C { }
  implicit def A2B(a: A): B = null
  implicit def C2B(a: C): B = null
  def foo: C = null

  /*start*/foo(1)/*end*/
}

//Int