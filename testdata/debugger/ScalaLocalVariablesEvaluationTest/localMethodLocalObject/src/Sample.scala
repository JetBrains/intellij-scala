object Sample {
  def main(args: Array[String]) {
    object x
    def foo(y: Int) {
      x
      "stop here"
    }
    foo(2)
  }
}