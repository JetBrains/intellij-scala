object Sample {
  def main(args: Array[String]) {
    val x: Int = 1
    var s = "a"
    def foo(y: Int) {
      "stop here"
      x
    }
    foo(2)
  }
}