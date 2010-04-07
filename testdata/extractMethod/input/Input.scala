class Input {
  def foo(x: Int) {
    val y = 34
    val z = ""
    /*start*/
    if (z == "") {
      val g = y + 1
      print(g)
    } else {
      print(x)
    }
    /*end*/
    if (x == 1) return
  }
}
/*
class Input {
  def testMethodName(z: String, y: Int, x: Int): Unit = {
    if (z == "") {
      val g = y + 1
      print(g)
    } else {
      print(x)
    }
  }

  def foo(x: Int) {
    val y = 34
    val z = ""
    /*start*/
    testMethodName(z, y, x)
    /*end*/
    if (x == 1) return
  }
}
*/