object Outer:
  class Indented:
    def f(i: Int): Unit =
      println(/*start*/i + 1/*end*/)

  def outside: Int = 0
/*
object Outer:
  class Indented:
    def f(i: Int): Unit =
      println(testMethodName(i))

    def testMethodName(i: Int): Int = {
      i + 1
    }

  def outside: Int = 0
*/