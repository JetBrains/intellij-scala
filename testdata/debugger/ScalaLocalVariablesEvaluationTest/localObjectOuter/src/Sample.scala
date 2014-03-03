object Sample {
  def main(args: Array[String]) {
    object x {}
    val runnable = new Runnable {
      def run() {
        val runnable = new Runnable {
          def run() {
            x
            "stop here"
          }
        }
        runnable.run()
      }
    }
    runnable.run()
  }
}