object GenericFunction {
  def foo[T](x: T => String) = "45"

  foo[Int](/*start*/_/*end*/.toString)
}
//Int