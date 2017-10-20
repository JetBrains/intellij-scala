class TestOverload(val foo: Int) {
  def foo(x: Int) = 2 * x

  def printFoo() {
    println(/*start*/3 + foo/*end*/)
  }
}
//Int