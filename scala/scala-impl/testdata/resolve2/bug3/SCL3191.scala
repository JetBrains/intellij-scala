object Exclude {
  def x_=(x: Int) {}
  def x = 1

  class O {
    val x = 2

    class D {
      /* line: 6 */x = 1
    }
  }
}