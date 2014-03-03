object Sample {
  def main(args: Array[String]) {
    val x = 1
    var s = "a"
    def foo(y: Int) {
      def foo(y: Int) {
        class A {
          def foo() {
           "stop here"
            s + x
          }
        }
        new A().foo()
      }
      foo(y)
    }
    foo(2)
  }
}