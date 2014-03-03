object Sample {
  def main(args: Array[String]) {
    val x = 1
    object X {
      def foo(y: Int) {
        "stop here"
         x
      }
    }
    X.foo(2)
  }
}