class Handler {
  def handle[A] : Handle[A] = null

  class Handle[A] {
    def apply[B](callback: A => B): (A) => B = null
  }

  def handle2[A, B](callback: A => B): (A) => B = null

  def testNok = /*resolved: true*/ handle[Int] { i => (i + 1).toString}
}


