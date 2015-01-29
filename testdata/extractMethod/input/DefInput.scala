class DefInput {
  def foo {
    def goo(x: Int): Int = x + 4

    val g = 77
    /*start*/
    g match {
      case 77 => print(g)
      case _ => print(goo(g))
    }
    /*end*/
    print("exit")
  }
}
/*
class DefInput {
  def foo {
    def goo(x: Int): Int = x + 4

    val g = 77
    /*start*/
    testMethodName(g, goo _)
    /*end*/
    print("exit")
  }

  def testMethodName(g: Int, goo: (Int) => Int): Unit = {
    g match {
      case 77 => print(g)
      case _ => print(goo(g))
    }
  }
}
*/