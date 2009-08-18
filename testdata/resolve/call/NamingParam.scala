object NamingParam {
  def foo(x: Int) = x

  def goo = foo(<ref>x = 34)
}