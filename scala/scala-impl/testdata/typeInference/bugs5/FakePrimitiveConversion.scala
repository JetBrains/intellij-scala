object FakePrimitiveConversion {
  def foo[T](x: Z => Option[T] => Unit, s: T) = 1
  def foo(x: Int) = "text"

  class Z {
    var z: Option[Int] = None
  }

  /*start*/foo(z => z.z_=, 123)/*end*/
}
//Int