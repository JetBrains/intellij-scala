class SynchronizedExample {
    final Object lock = new Object();

    void foo() {
        synchronized (lock) {
            try {
                lock.wait(100);
            } catch (InterruptedException ignore) {
            }
        }

        synchronized(this) {

        }
    }
}
/*
class SynchronizedExample {
  final val lock = new Any

  def foo(): Unit = {
    lock.synchronized {
      try lock.wait(100)
      catch {
        case ignore: InterruptedException =>
      }
    }
    this.synchronized {
    }
  }
}
*/