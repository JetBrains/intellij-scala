object NoSearchGeneric {
  abstract class GenericParamInput[T] {
    val x: T
    def foo {
      val y = x
      /*start*/
      println(y)
      /*end*/

      println("a")
    }
  }
}
/*
object NoSearchGeneric {
  abstract class GenericParamInput[T] {
    val x: T
    def foo {
      val y = x
      /*start*/
      testMethodName(y)
      /*end*/

      println("a")
    }
  }

  def testMethodName[T](y: T): Unit = {
    println(y)
  }
}
*/