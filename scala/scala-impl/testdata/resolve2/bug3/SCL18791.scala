object SCL18791 {
  def foo(a: Int, @deprecatedName("fx") b: Int) = 123
  def foo(z: Int, fx: Boolean) = false
  foo(a = 1 , /* line: 2, name: b */fx = 123)
}