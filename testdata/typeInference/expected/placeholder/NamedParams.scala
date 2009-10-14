object NamedParams {
  def foo(x: String, y: String => String) = y(x)
  def foo(z: Int => String) = z(5)

  foo(y = /*start*/_.length.toString/*end*/, x = "")
}
//(String) => String