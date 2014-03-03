object Sample {
  def foo(x: String*) = x.foldLeft("")(_ + _)
  def main(args: Array[String]) {
    "stop here"
  }
}