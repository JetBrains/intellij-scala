object IntSum {
  def foo(x: (Int, Int) => Int) = x(5,6)

  foo(/*start*/_ + _/*end*/)
}
//(Int, Int) => Int