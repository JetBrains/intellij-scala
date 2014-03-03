object Sample {
  def foo() = 2
  val x = 1
  def main(args: Array[String]) {
    def moo() {}
    class A {
      val x = 1
      def goo() = 2
      def foo() {
        val r = () => {
          moo()
          x
          "stop here"
        }
        r()
      }
    }

    new A().foo()
  }
}