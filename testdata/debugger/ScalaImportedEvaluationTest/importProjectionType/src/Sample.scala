object Sample {

  def main(args: Array[String]) {
    new A().foo()
  }
}

class A {
  class B {
    val y = List(1, 2, 3)
  }

  val x = "abc"

  def foo() {
    val b = new B
    import b.y._
    import x._

    "stop here"
  }

}