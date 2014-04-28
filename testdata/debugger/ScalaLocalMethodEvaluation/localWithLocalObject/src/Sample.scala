object Sample {
  def main(args: Array[String]) {
    object y {val y = 1}
    val x = 2
    def foo: Int = x - y.y
    "stop here"
  }
}