object Sample {
  def main(args: Array[String]) {
    class This {
      val x = 1
      def foo() {
       "stop here"
      }
    }
    new This().foo()
  }
}