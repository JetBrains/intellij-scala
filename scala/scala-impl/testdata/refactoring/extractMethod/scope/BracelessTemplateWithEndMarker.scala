object Indented:
  def f(i: Int): Unit =
    println(/*start*/i + 1/*end*/)
end Indented
/*
object Indented:
  def f(i: Int): Unit =
    println(testMethodName(i))

  def testMethodName(i: Int): Int = {
    i + 1
  }
end Indented
*/