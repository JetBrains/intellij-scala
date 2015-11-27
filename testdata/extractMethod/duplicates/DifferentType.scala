object DifferentType {
  def foo(i: Int) {
    val q = i
/*start*/

    val x = i
    println(i)
/*end*/

  }

  def foofoo(y: Int) {
    val x = "i"
    println("i")
  }
}
/*
object DifferentType {
  def foo(i: Int) {
    val q = i


    testMethodName(i)


  }

  def testMethodName(i: Int): Unit = {
    val x = i
    println(i)
  }

  def foofoo(y: Int) {
    val x = "i"
    println("i")
  }
}
*/