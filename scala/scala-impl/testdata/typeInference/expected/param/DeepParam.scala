object DeepParam {
  def foo(y: Int, x: Int => Int) = 34

  foo(2, if (true) y => y else y => /*start*/y/*end*/)
}
//Int