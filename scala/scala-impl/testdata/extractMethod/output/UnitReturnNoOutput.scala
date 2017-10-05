class UnitReturnNoOutput {
  def foo(i: Int) {
/*start*/
    if (true) return
    println(i)
/*end*/
    println()
  }
}
/*
class UnitReturnNoOutput {
  def foo(i: Int) {

    if (testMethodName(i)) return

    println()
  }

  def testMethodName(i: Int): Boolean = {
    if (true) return true
    println(i)
    false
  }
}*/