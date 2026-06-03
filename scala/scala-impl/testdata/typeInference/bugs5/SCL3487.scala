object SCL3487 {
  class A[T](val t: T)
  class B[T](val t: T)
  implicit val a: A[String] = new A("text")
  implicit val b: B[Int] = new B(1)
  def foo[TA: A, TB](implicit b: B[TB]): (TA, TB) = null

  /*start*/foo/*end*/
}
//(String, Int)