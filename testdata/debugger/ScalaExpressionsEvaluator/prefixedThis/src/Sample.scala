object Sample {
  def main(args: Array[String]) {
    class This {
      val x = 1
      def foo() {
        val runnable = new Runnable {
          def run() {
            val x = () => {
             This.this.x //to have This.this in scope
             "stop here"
            }
            x()
          }
        }
        runnable.run()
      }
    }
    new This().foo()
  }
}