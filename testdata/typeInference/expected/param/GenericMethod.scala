object GenericMethod {
  def foo[T](x: T => Int, y: T) = x(y)

  foo[Int](x => /*start*/x/*end*/, 34)
}
//Int