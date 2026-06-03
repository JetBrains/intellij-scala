class B {
  def foo(): String = ???
}

object B {
  implicit def toB(double: Double): B = new B()
}

class A {
  import B._
  /*start*/(42  foo)/*end*/
}
//String