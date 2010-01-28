object TypedPlaceholder {
  def foo[T](x: T => Int, y: T) = x(y)

  foo(/*start*/(_: String).length/*end*/, "")
}
//(String) => Int