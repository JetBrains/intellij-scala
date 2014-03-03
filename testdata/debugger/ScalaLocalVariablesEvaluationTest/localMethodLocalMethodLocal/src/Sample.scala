object Sample {
  def main(args: Array[String]) {
    val x = 1
    var s = "a"
    def foo(y: Int) {
      def foo(y: Int) {
        "stop here"
         x
      }
      foo(y)
    }
    foo(2)
  }
}