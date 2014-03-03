object Sample {
  def main(args: Array[String]) {
    def outer() {
      def inner(a: String, b: String = "default", c: String = "other"): String = {
        "stop here"
        a + b + c
      }
      inner("aa")
    }
    outer()
  }
}