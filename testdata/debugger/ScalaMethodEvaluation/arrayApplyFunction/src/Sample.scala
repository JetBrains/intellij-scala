object Sample {
  def main(args : Array[String]) {
    val s = Array.ofDim[String](2, 2)
    s(1)(1) = "test"
    "stop here"
  }
}