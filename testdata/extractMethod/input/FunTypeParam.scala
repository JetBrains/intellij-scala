class FunTypeParam {
  def f[T](x: T) {
    val y = x
    /*start*/
    x
    y
    /*end*/
  }
}
/*
class FunTypeParam {
  def f[T](x: T) {
    val y = x
    /*start*/
    testMethodName(x, y)
    /*end*/
  }

  def testMethodName[T](x: T, y: T): Unit = {
    x
    y
  }
}

*/