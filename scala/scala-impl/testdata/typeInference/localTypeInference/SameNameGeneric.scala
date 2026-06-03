object SameNameGeneric {
  def foo[T, F](x: T, y: F) = {
    /*start*/foo(x, y, 34)/*end*/
  }

  def foo[T, F, Z](x: T, y: F, z: Z) = {
    (x, y, z)
  }
}
//(T, F, Int)