object MethodCallParams {
  def goo(x: Int, y: String, z: Int): Int = x + y.length + z

  def foo(x: (Int, Int) => Int) = x(239, 45)

  foo(/*start*/goo(_, "446", _)/*end*/)
}
//(Int, Int) => Int