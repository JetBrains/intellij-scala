object Sample {
  def main(args: Array[String]) {
    def outer() {
      val s = "start"
      def inner(a: String, b: String): String = {
        "stop here"
        s + a + b
      }
      inner("aa", "bb")
    }
    outer()
  }
}