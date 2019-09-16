import java.io.{Closeable, InputStream}

object Test {
  def foo() {
    val runnable = new Runnable {
      override def run() {}
    }
    runnable.run()
//        val runnable2: Runnable = println _
//        runnable2.run()
    val closeableRunnable = new Runnable with Closeable {
      override def close() {}
      override def run() {}
    }
    val runnableIs = new InputStream with Runnable {
      override def read(): Int = 0
      override def run() {}
    }
  }
}
