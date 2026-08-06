package test

class Foo[T](val i: Int)

class ReturnSeveralOutput3 {
  def foo(i: Int): Int = {
/*start*/
    val x = new Foo[_](1)
    val y = new Foo[_](2)
/*end*/
    x.i + y.i
  }
}
/*
package test

class Foo[T](val i: Int)

class ReturnSeveralOutput3 {
  def foo(i: Int): Int = {

    val (x: Foo[_], y: Foo[_]) = testMethodName

    x.i + y.i
  }

  def testMethodName: (Foo[_], Foo[_]) = {
    val x = new Foo[_](1)
    val y = new Foo[_](2)
    (x, y)
  }
}
*/