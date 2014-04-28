object Sample {
  def main(args: Array[String]) {
    val a = new A(0)
    import a._
    "stop here"
  }
}

class A(val i: Int) {
  val x = 0
  def foo() = "foo"
}