class DeepNamingParam {
  def foo(y: Int, x: Int => Int) = 44

  foo(x = {2; x => /*start*/x/*end*/}, y = 34)
}
//Int