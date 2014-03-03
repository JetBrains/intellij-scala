object Sample {
  def main(args: Array[String]) {
    val x = 1
    var y = "a"
    val runnable = new Runnable {
      def run() {
        val foo = () => {
          val runnable = new Runnable {
            def run() {
              x
              "stop here"
            }
          }
          runnable.run()
        }
        foo()
      }
    }
    runnable.run()
  }
}