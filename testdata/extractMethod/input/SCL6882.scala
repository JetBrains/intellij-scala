class test {
  case class A(a:Int, b:Int)
  def f (a: Int, b:Int) = {
    /*start*/
    A(a, b)
    /*end*/
  }
}
/*
class test {
  case class A(a:Int, b:Int)
  def f (a: Int, b:Int) = {
    /*start*/
    testMethodName(a, b)
    /*end*/
  }

  def testMethodName(a: Int, b: Int): A = {
    A(a, b)
  }
}
*/