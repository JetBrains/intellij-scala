object Sample {
  def main(args: Array[String]) {
    val x = 1
    def foo(y: Int) {
      def foo(y: Int) {
        class A {
          def foo() {
            class B {
              def foo() {
                def goo(y: Int) {
                  "stop here"
                  x
                }
                goo(x + 1)
              }
            }
            new B().foo()
          }
        }
        new A().foo()
      }
      foo(y)
    }
    foo(2)
  }
}