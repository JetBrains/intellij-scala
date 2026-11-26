object Indented:
  def f(i: Int): Unit =
    println(/*start*/i + 1/*end*/)

  def following: Int = 0
/*
object Indented:
  def f(i: Int): Unit =
    println(testMethodName(i))

  def testMethodName(i: Int): Int = {
    i + 1
  }

  def following: Int = 0
*/