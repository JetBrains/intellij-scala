object Test {
  def foo(x: (Int, Int => Int)) =1

  foo(1, p => /*start*/p/*end*/)
}
//Int