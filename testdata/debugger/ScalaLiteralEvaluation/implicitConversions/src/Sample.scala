object Sample {
  implicit def intToString(x: Int) = x.toString + x.toString
  def main(args: Array[String]) {
    "stop here"
  }
}