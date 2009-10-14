object LongInfix {
  def foo(x: (Int, Int, Int, Int) => Int) = x(1, 2, 3, 4)

  foo(/*start*/_ + _ * _ + _/*end*/)
}
//(Int, Int, Int, Int) => Int