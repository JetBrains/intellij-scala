class Handler {
  def handle[A] = new Handle[A]

  class Handle[A] {
    def apply[B](callback: A => B) = callback.apply(_)
  }

  def handle2[A, B](callback: A => B) = callback.apply(_)

  def testNok = /*resolved: true*/ handle[Int] {i => (i + 1).toString}

  def testOk = /*resolved: true*/handle2 {i: Int => (i + 1).toString}
}


