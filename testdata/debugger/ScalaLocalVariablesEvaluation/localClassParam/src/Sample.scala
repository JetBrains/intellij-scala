class A(x: Int) {
  val h = x
  def foo() {
    val y = () => {
      "stop here"
      1 + 2 + x
    }
    y()
  }
}
object Sample {
  def main(args: Array[String]) {
    val a = new A(1)
    a.foo()
  }
}