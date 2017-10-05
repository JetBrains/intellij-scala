trait T {
  def apply(x: Double): Double
}

trait Base {
  def foo: T

  /*resolved: true: applicable: true*/ foo(1d)
}

class Derived1 extends Base {
  val foo: T = null
  /*resolved: true: applicable: true*/ foo(1d)
}
()