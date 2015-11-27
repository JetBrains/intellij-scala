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

    testMethodName(x, y)

  }

  def testMethodName[T](x: T, y: T): Unit = {
    x
    y
  }
}

*/