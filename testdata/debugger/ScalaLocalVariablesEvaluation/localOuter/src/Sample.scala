object Sample {
  def main(args: Array[String]) {
    val x = 1
    val runnable = new Runnable {
      def run() {
        x
        "stop here"
      }
    }
    runnable.run()
  }
}