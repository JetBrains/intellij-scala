abstract class GenericParamInput[T] {
  val x: T

  def foo {

    val y = x
/*start*/
    println(y)
/*end*/
  }
}
/*
abstract class GenericParamInput[T] {
  val x: T

  def foo {

    val y = x

    testMethodName(y)

  }

  def testMethodName(y: T): Unit = {
    println(y)
  }
}
*/