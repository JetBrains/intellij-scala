object Sample {
  def main(args: Array[String]) {
    class Local {
      def foo() {
        val x = 1
        var s = "a"
        object X {
          def foo(y: Int) {
            "stop here"
             x
          }
        }
        X.foo(2)
      }
    }
    new Local().foo()
  }
}