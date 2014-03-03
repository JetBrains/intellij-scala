object Sample {
  def main(args: Array[String]) {
    val g = 1
    def moo(x: Int) = g + x
    val zz = (y: Int) => {
      val uu = (x: Int) => {
        g
        "stop here"
      }
      uu(1)
    }
    zz(2)
  }
}