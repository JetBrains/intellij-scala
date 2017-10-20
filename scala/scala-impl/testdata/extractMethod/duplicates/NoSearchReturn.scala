object NoSearchReturn {
  def foo(i: Int) {
    val y = i
/*start*/
    println(y)
    if (true) return
    else println(y)
/*end*/

    println("a")
  }

  def foofoo(y: Int) {
    println(1)
    if (true) return
    else println(1)
  }
}
/*
object NoSearchReturn {
  def foo(i: Int) {
    val y = i

    if (testMethodName(y)) return


    println("a")
  }

  def testMethodName(y: Int): Boolean = {
    println(y)
    if (true) return true
    else println(y)
    false
  }

  def foofoo(y: Int) {
    println(1)
    if (true) return
    else println(1)
  }
}
*/