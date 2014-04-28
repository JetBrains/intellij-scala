object Sample {
  def foo() = 2
  def main(args: Array[String]) {
    val x = 1
    val r = () => {
      x
      "stop here"
    }
    r()
  }
}