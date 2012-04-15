abstract class GenericParamInput[T] {
  val x: T
  def foo {
    val y = x
    /*start*/
    y
    /*end*/
  }
}
/*
abstract class GenericParamInput[T] {
  val x: T
  def foo {
    val y = x
    /*start*/
    testMethodName(y)
    /*end*/
  }

  def testMethodName(y: T) {
    y
  }
}
*/