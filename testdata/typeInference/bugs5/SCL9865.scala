package something

trait B {
  def foo(): Int = 123
}

object X {
  class A
}

object Y {
  class A extends Object
}

object Z {
  /*start*/(new X.A().foo(), new Y.A().foo())/*end*/
}
//(Int, Int)