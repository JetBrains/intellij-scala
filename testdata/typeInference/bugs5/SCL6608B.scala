object SCL6608B {
  def g(s: String): s.type = ???
  def foo(x : String): x.type = /*start*/g(x)/*end*/
}
//x.type