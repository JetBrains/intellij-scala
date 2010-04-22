class InsideClojure {
  def foo = {
    {p: Int => {
      /*start*/
      p + 1
      p + 2
      /*end*/
    }}
  }
}
/*
class InsideClojure {
  def testMethodName(p: Int): Int = {
    p + 1
    p + 2
  }

  def foo = {
    {p: Int => {
      /*start*/
      testMethodName(p)
      /*end*/
    }}
  }
}
*/