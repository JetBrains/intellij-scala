object NamingParam {
  def foo(x: Int => Int) = 44
  foo(x = x => /*start*/x/*end*/)
}
//Int