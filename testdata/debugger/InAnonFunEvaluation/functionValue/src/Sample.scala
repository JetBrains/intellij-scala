object Sample {
  def main(args: Array[String]) {
    val a = "a"
    var b = "b"
    val f: (Int) => Unit = n => {
      val x = "x"
      "stop here"
    }
    f(10)
  }
}