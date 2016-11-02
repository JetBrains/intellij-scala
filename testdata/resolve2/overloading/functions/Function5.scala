object Test {
  def s(x: Int): Int = 1
  object s {
    def unapply(x : String): Option[Int] = null
  }

  val x: Int => Int = /*resolved: true, line: 2 */s
}