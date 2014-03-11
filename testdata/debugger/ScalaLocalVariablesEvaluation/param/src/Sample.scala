object Sample {
  def foo(x: Int) {
    "stop here"
  }

  def main(args: Array[String]) {
    val x = 0
    foo(x + 1)
  }
}