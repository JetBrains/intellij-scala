object Sample {
  def moo(x: String*) = x.foldLeft(0)(_ + _.length())
  def main(args: Array[String]) {
    val x = Seq("a", "b")
    "stop here"
  }
}