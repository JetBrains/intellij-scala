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
  def testMethodName(y: T) {
    y
  }

  def foo {
    val y = x
    /*start*/
    testMethodName(y)
    /*end*/
  }
}
*/