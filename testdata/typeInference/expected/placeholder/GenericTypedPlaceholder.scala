object GenericTypedPlaceholder {
  def foo[T](x: T => Int, y: T) = x(y)

  foo[String](/*start*/_.length/*end*/, "")
}
//(String) => Int